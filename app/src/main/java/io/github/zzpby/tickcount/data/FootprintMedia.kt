package io.github.zzpby.tickcount.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/**
 * The media a footprint carries, kept in the app's private directory.
 *
 * Bytes are copied rather than decoded-and-re-encoded, because the point of
 * this page is what was captured *as it was captured*: an animated GIF has to
 * stay animated, and a video cannot be a bitmap at all. The format travels
 * from the picker's own MIME type, and the file name says what it is.
 *
 * Thumbnails are decoded on demand — a still or a GIF's first frame the
 * ordinary way, a video's frame through the retriever — so the grid stays
 * cheap while the files stay whole.
 */
object FootprintMedia {

    private const val DIR = "footprint-media"

    /** The extensions this page can hold, keyed by the MIME type that names them. */
    private val EXTENSION_BY_MIME = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/heic" to "heic",
        "video/mp4" to "mp4",
        "video/webm" to "webm",
        "video/3gpp" to "3gp",
        "video/x-matroska" to "mkv",
        "video/quicktime" to "mov",
    )

    private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "3gp", "mkv", "mov")
    private val ANIMATED_EXTENSIONS = setOf("gif")

    /** The whole app's own copy, one file per id: `fp-<id>-<stamp>.<ext>`. */
    private const val PREFIX = "fp-"

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String?): File? {
        if (name == null || !name.startsWith(PREFIX)) return null
        val file = File(dir(context), name)
        return file.takeIf { it.isFile }
    }

    /** True when the name is a video — the viewer plays it rather than showing it. */
    fun isVideo(name: String): Boolean = extensionOf(name) in VIDEO_EXTENSIONS

    /** True when the name is an animated GIF — the viewer animates it on API 28+. */
    fun isAnimated(name: String): Boolean = extensionOf(name) in ANIMATED_EXTENSIONS

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', missingDelimiterValue = "")

    /**
     * Copies whatever the picker handed over, whole and unconverted, and
     * returns the file name to store — or null for a type this page cannot
     * hold, which the caller simply drops.
     */
    fun save(context: Context, footprintId: String, uri: Uri): String? {
        return runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)
            val extension = EXTENSION_BY_MIME[mime] ?: return null
            val file = File(dir(context), "$PREFIX$footprintId-${System.nanoTime()}.$extension")
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file.name
        }.getOrNull()
    }

    /** Deletes the media file called [name], if it is one of ours. */
    fun delete(context: Context, name: String?) {
        if (name == null || !name.startsWith(PREFIX)) return
        File(dir(context), name).delete()
    }

    /**
     * A thumbnail of at most [maxEdge] on its long side: an image the ordinary
     * sampled way — which is also a GIF's first frame — and a video through
     * its retriever, a second in.
     */
    fun thumbnail(context: Context, name: String?, maxEdge: Int = 480): Bitmap? {
        val file = file(context, name) ?: return null
        return runCatching {
            if (isVideo(file.name)) videoFrame(file, maxEdge) else stillFrame(file, maxEdge)
        }.getOrNull()
    }

    private fun stillFrame(file: File, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun videoFrame(file: File, maxEdge: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000)
                ?: retriever.frameAtTime ?: return null
            val longest = maxOf(frame.width, frame.height)
            if (longest <= maxEdge) return frame
            val scale = maxEdge.toFloat() / longest
            return Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } finally {
            retriever.release()
        }
    }
}
