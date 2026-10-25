package com.neoruaa.xhsdn.ui

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neoruaa.xhsdn.R
import com.neoruaa.xhsdn.utils.decodeSampledBitmap
import com.neoruaa.xhsdn.viewmodels.MediaItem
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Uses app-owned URI access instead of depending on an installed external gallery. */
@Composable
fun StoredImageViewer(
    modifier: Modifier = Modifier,
    images: List<MediaItem>,
    initialPath: String,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = images.indexOfFirst { it.path == initialPath }.coerceAtLeast(0),
        pageCount = { images.size }
    )
    BackHandler(onBack = onDismiss)
    Column(modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(text = stringResource(R.string.back_content_description), onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.detail_image_position, pager.currentPage + 1, images.size), color = Color.White)
            }
            HorizontalPager(
                state = pager,
                key = { images[it].path },
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                StoredImagePage(item = images[page], modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private data class ImageLoad(val bitmap: ImageBitmap? = null, val complete: Boolean = false)

@Composable
private fun StoredImagePage(modifier: Modifier = Modifier, item: MediaItem) {
    val context = LocalContext.current.applicationContext
    val image by produceState(ImageLoad(), item.media) {
        value = ImageLoad()
        value = withContext(Dispatchers.IO) {
            ImageLoad(context.decodeSampledBitmap(item.media, 2048, 2048)?.asImageBitmap(), true)
        }
    }
    var motionFile by remember(item.path) { mutableStateOf<java.io.File?>(null) }
    var playing by remember(item.path) { mutableStateOf(false) }
    var videoView by remember { mutableStateOf<android.widget.VideoView?>(null) }
    DisposableEffect(item.path) {
        onDispose {
            videoView?.stopPlayback()
            motionFile?.delete()
        }
    }
    LaunchedEffect(item.path) {
        motionFile = withContext(Dispatchers.IO) { extractMotionVideo(context, item) }
    }
    var scale by remember(item.path) { mutableFloatStateOf(1f) }
    var offset by remember(item.path) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val bitmap = image.bitmap
        val fit = if (bitmap != null) minOf(
            viewport.width.toFloat() / bitmap.width,
            viewport.height.toFloat() / bitmap.height
        ) else 0f
        val maxX = ((bitmap?.width ?: 0) * fit * scale - viewport.width).coerceAtLeast(0f) / 2
        val maxY = ((bitmap?.height ?: 0) * fit * scale - viewport.height).coerceAtLeast(0f) / 2
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX),
            (offset.y + pan.y).coerceIn(-maxY, maxY))
    }
    Box(modifier.clipToBounds().onSizeChanged { viewport = it }
        .transformable(state = transform, canPan = { scale > 1f })
        .pointerInput(item.path) {
            detectTapGestures(onPress = {
                try {
                    withTimeoutOrNull(android.view.ViewConfiguration.getLongPressTimeout().toLong()) { tryAwaitRelease() }
                        ?: run { playing = motionFile != null; tryAwaitRelease() }
                } finally { playing = false }
            }, onDoubleTap = {
                scale = if (scale > 1f) 1f else 2.5f
                offset = Offset.Zero
            })
        }, contentAlignment = Alignment.Center) {
        val bitmap = image.bitmap
        if (playing && motionFile != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    android.widget.VideoView(viewContext).also { view ->
                        videoView = view
                        view.setOnPreparedListener { player ->
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            view.start()
                        }
                        view.setVideoPath(motionFile!!.absolutePath)
                    }
                },
                onRelease = { it.stopPlayback(); videoView = null }
            )
        } else if (bitmap != null) {
            Image(bitmap, contentDescription = item.media.displayName,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                })
        } else {
            Text(stringResource(if (image.complete) R.string.detail_image_unavailable else R.string.detail_image_loading),
                modifier = Modifier.padding(24.dp))
        }
    }
}
