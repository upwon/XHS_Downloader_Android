package com.neoruaa.xhsdn.feature.detail

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neoruaa.xhsdn.DetailActivity
import com.neoruaa.xhsdn.R
import com.neoruaa.xhsdn.XHSApplication
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoredImageViewerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private val app = ApplicationProvider.getApplicationContext<XHSApplication>()

    @Test fun detailImageCardOpensPreviewAndPagerSwipesBothDirections() {
        val directory = File(app.cacheDir, "detail-viewer-test-${System.nanoTime()}").apply { mkdirs() }
        val files = (0..2).map { index ->
            val file = File(directory, "image-$index.png")
            val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(index * 80, 100, 150))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file
        }
        val scenario = ActivityScenario.launch<DetailActivity>(
            DetailActivity.newIntent(app, "", "Viewer test", files.map(File::getAbsolutePath))
        )
        try {
            clickDescription(files[1].absolutePath)
            awaitText(app.getString(R.string.detail_image_position, 2, 3))
            awaitDescription(files[1].absolutePath)
            swipe(forward = true)
            awaitText(app.getString(R.string.detail_image_position, 3, 3))
            awaitDescription(files[2].absolutePath)
            swipe(forward = false)
            awaitText(app.getString(R.string.detail_image_position, 2, 3))
        } finally {
            scenario.close()
            directory.deleteRecursively()
        }
    }

    private fun clickDescription(description: String) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            val target = findNode(automation.rootInActiveWindow, { it.contentDescription?.toString() == description })
            if (target != null) {
                var clickable: AccessibilityNodeInfo? = target
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    instrumentation.waitForIdleSync()
                    return
                }
            }
            SystemClock.sleep(100)
        }
        val tree = accessibilityTree(automation.rootInActiveWindow)
        Log.e("StoredImageViewerTest", "Image tile was not clickable: $description\n$tree")
        assertTrue("Image tile was not clickable: $description", false)
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

    private fun awaitText(text: String) = awaitNode("text=$text") { it.text?.toString() == text }
    private fun awaitDescription(description: String) =
        awaitNode("description=$description") { it.contentDescription?.toString() == description }

    private fun awaitNode(expected: String, predicate: (AccessibilityNodeInfo) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            if (findNode(automation.rootInActiveWindow, predicate) != null) return
            SystemClock.sleep(100)
        }
        val tree = accessibilityTree(automation.rootInActiveWindow)
        Log.e("StoredImageViewerTest", "Expected accessibility node was not displayed: $expected\n$tree")
        assertTrue("Expected accessibility node was not displayed: $expected", findNode(automation.rootInActiveWindow, predicate) != null)
    }

    private fun accessibilityTree(node: AccessibilityNodeInfo?, depth: Int = 0): String = buildString {
        if (node == null) return@buildString
        append("  ".repeat(depth))
        append("text=").append(node.text).append(", description=").append(node.contentDescription)
            .append(", clickable=").append(node.isClickable).append('\n')
        for (index in 0 until node.childCount) append(accessibilityTree(node.getChild(index), depth + 1))
    }

    private fun findNode(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            findNode(root.getChild(index), predicate)?.let { return it }
        }
        return null
    }
}
