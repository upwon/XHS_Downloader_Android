package com.neoruaa.xhsdn.ui

import androidx.compose.foundation.Image
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
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = image.bitmap
        if (bitmap != null) {
            Image(bitmap, contentDescription = item.media.displayName,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text(stringResource(if (image.complete) R.string.detail_image_unavailable else R.string.detail_image_loading),
                modifier = Modifier.padding(24.dp))
        }
    }
}
