package com.neoruaa.xhsdn.feature.detail

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neoruaa.xhsdn.MainActivity
import com.neoruaa.xhsdn.R
import com.neoruaa.xhsdn.XHSApplication
import com.neoruaa.xhsdn.data.storage.StoredMediaRef
import com.neoruaa.xhsdn.ui.StoredImageViewer
import com.neoruaa.xhsdn.viewmodels.MediaItem
import com.neoruaa.xhsdn.viewmodels.MediaType
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class StoredImageViewerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private val app = ApplicationProvider.getApplicationContext<XHSApplication>()

    @Test fun opensSelectedContentUriAndSwipesBothDirections() {
        val directory = File(app.cacheDir, "viewer-test-${System.nanoTime()}").apply { mkdirs() }
        val images = (0..2).map { index ->
            val file = File(directory, "image-$index.png")
            val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(index * 80, 100, 150))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val ref = if (index == 1) StoredMediaRef(
                FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).toString(),
                file.name, "image/png"
            ) else StoredMediaRef.fromLegacyFile(file, "image/png")
            MediaItem(ref, MediaType.IMAGE)
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity -> activity.setContent { MiuixTheme {
                var previewOpen by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.size(96.dp)
                            .align(Alignment.Center)
                            .clickable { previewOpen = true }
                            .semantics { contentDescription = "open preview" }
                    )
                    if (previewOpen) {
                        StoredImageViewer(
                            modifier = Modifier.fillMaxSize(),
                            images = images,
                            initialPath = images[1].path,
                            onDismiss = { previewOpen = false }
                        )
                    }
                }
            } } }
            tapCenter()
            awaitText(app.getString(R.string.detail_image_position, 2, 3))
            awaitDescription("image-1.png")
            swipe(forward = true)
            awaitText(app.getString(R.string.detail_image_position, 3, 3))
            awaitDescription("image-2.png")
            swipe(forward = false)
            awaitText(app.getString(R.string.detail_image_position, 2, 3))
        } finally {
            scenario.close()
            directory.deleteRecursively()
        }
    }

    private fun swipe(forward: Boolean) {
        val metrics = app.resources.displayMetrics
        val start = metrics.widthPixels * if (forward) 0.85f else 0.15f
        val end = metrics.widthPixels - start
        val y = metrics.heightPixels * 0.55f
        val down = SystemClock.uptimeMillis()
        for (step in 0..20) {
            val event = MotionEvent.obtain(down, down + step * 16L,
                when (step) { 0 -> MotionEvent.ACTION_DOWN; 20 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE },
                start + (end - start) * step / 20, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { automation.injectInputEvent(event, true) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun tapCenter() {
        val metrics = app.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val y = metrics.heightPixels / 2f
        val down = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_UP, x, y, 0)
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            automation.injectInputEvent(downEvent, true)
            automation.injectInputEvent(upEvent, true)
        } finally {
            downEvent.recycle()
            upEvent.recycle()
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitText(text: String) = awaitNode { it.text?.toString() == text }
    private fun awaitDescription(text: String) = awaitNode { it.contentDescription?.toString() == text }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean) {
        fun contains(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (predicate(node)) return true
            return (0 until node.childCount).any { contains(node.getChild(it)) }
        }
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            if (contains(automation.rootInActiveWindow)) return
            SystemClock.sleep(100)
        }
        assertTrue("Expected viewer node was not displayed", contains(automation.rootInActiveWindow))
    }
}
