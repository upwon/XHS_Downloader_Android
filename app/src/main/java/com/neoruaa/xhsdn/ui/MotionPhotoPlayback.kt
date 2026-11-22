package com.neoruaa.xhsdn.ui

import android.content.Context
import android.media.MediaExtractor
import com.neoruaa.xhsdn.viewmodels.MediaItem
import com.neoruaa.xhsdn.viewmodels.MediaType
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Extracts the appended MP4 in the merged motion-photo formats produced by this app. */
internal suspend fun extractMotionVideo(context: Context, item: MediaItem, companions: List<MediaItem> = emptyList()): File? {
    val stem = item.media.displayName.substringBeforeLast('.').removeSuffix("_live_MP")
    val paired = companions.firstOrNull {
        it.type == MediaType.VIDEO && it.media.displayName.substringBeforeLast('.') in
            setOf("${stem}_motion", "${stem}_live_MP")
    }
    val sourceItem = paired ?: item
    val source = File.createTempFile("motion-source-", ".jpg", context.cacheDir)
    var output: File? = null
    try {
        val input = sourceItem.media.legacyPath?.let { File(it).inputStream() }
            ?: context.contentResolver.openInputStream(sourceItem.media.androidUri) ?: return null
        input.use { stream -> source.outputStream().use { target ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                target.write(buffer, 0, count)
            }
        } }
        RandomAccessFile(source, "r").use { file ->
            val bytes = ByteArray(65536)
            var base = 0L
            while (base < file.length()) {
                currentCoroutineContext().ensureActive()
                file.seek(base)
                val count = file.read(bytes)
                if (count < 8) break
                for (index in 4 until count - 3) {
                    if (bytes[index] != 0x66.toByte() || bytes[index + 1] != 0x74.toByte() ||
                        bytes[index + 2] != 0x79.toByte() || bytes[index + 3] != 0x70.toByte()) continue
                    val start = base + index - 4
                    val extractor = MediaExtractor()
                    val valid = try {
                        java.io.FileInputStream(source).use { extractor.setDataSource(it.fd, start, file.length() - start) }
                        (0 until extractor.trackCount).any {
                            extractor.getTrackFormat(it).getString("mime")?.startsWith("video/") == true
                        }
                    } catch (_: java.io.IOException) { false }
                    catch (_: IllegalArgumentException) { false }
                    finally { extractor.release() }
                    if (valid) {
                        val result = File.createTempFile("motion-play-", ".mp4", context.cacheDir)
                        output = result
                        file.seek(start)
                        result.outputStream().use { target ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val size = file.read(bytes)
                                if (size < 0) break
                                target.write(bytes, 0, size)
                            }
                        }
                        output = null
                        return result
                    }
                }
                base += count - 7
            }
        }
        return null
    } catch (_: java.io.IOException) {
        return null
    } catch (_: SecurityException) {
        return null
    } finally {
        source.delete()
        output?.delete()
    }
}
