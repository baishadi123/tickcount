package io.github.zzpby.tickcount.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import io.github.zzpby.tickcount.domain.MediaRef
import java.io.File

/**
 * The media a footprint carries — the album where the system allows a
 * reference, the app's private directory for the copies that fall back to
 * it.
 *
 * A reference streams straight from the album; a copied file keeps its bytes
 * whole rather than decoded-and-re-encoded, because the point of this page
 * is what was captured *as it was captured*: an animated GIF has to stay
 * animated, and a video cannot be a bitmap at all. The format travels from
 * the picker's own MIME type, and the file name says what it is.
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

    // A name is only ours if this store minted it: a hand-made backup must not
    // walk out of the directory with a separator in the name.
    private fun isOurs(name: String): Boolean =
        name.startsWith(PREFIX) && name.none { it == '/' || it == '\\' }

    fun file(context: Context, name: String?): File? {
        if (name == null || !isOurs(name)) return null
        val file = File(dir(context), name)
        return file.takeIf { it.isFile }
    }

    /**
     * Where a viewer plays from: a gallery entry streams from its
     * MediaStore uri, one of our own files opens by path.
     */
    fun mediaUri(context: Context, entry: String?): Uri? =
        when {
            entry == null -> null
            MediaRef.isRef(entry) -> GalleryMedia.mediaUri(context, entry)
            else -> file(context, entry)?.let { Uri.fromFile(it) }
        }

    /** True when the entry is a moving picture — played rather than shown. */
    fun isVideo(entry: String): Boolean =
        if (MediaRef.isRef(entry)) GalleryMedia.isVideo(entry) else extensionOf(entry) in VIDEO_EXTENSIONS

    /** True when the entry is an animated GIF — animated on API 28+. */
    fun isAnimated(entry: String): Boolean =
        if (MediaRef.isRef(entry)) GalleryMedia.isAnimated(entry) else extensionOf(entry) in ANIMATED_EXTENSIONS

    /**
     * The picture whole, for a full-screen page: the album's original for a
     * reference, one of our own files as it was copied — turned upright
     * either way, the tag read and honoured. Whatever the strip does, the
     * reading itself is never a miniature and never sideways.
     */
    fun bitmap(context: Context, entry: String): Bitmap? {
        if (MediaRef.isRef(entry)) return GalleryMedia.bitmap(context, entry)
        val own = file(context, entry) ?: return null
        return runCatching {
            val decoded = BitmapFactory.decodeFile(own.absolutePath) ?: return null
            GalleryMedia.upright(decoded, GalleryMedia.uprightOf(own))
        }.getOrNull()
    }

    /** The MIME type one of our own file names stands for, by its extension. */
    fun mimeOf(name: String): String? =
        EXTENSION_BY_MIME.entries.firstOrNull { it.value == extensionOf(name) }?.key

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

    /**
     * Takes what the picker offered, as a gallery reference where the system
     * allows one — the album stays the only original, and the phone carries
     * no second copy — falling back to a private copy on systems too old to
     * hold a reference, or where the reference could not be read.
     */
    fun ingest(context: Context, footprintId: String, uri: Uri): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            GalleryMedia.createRef(context, uri) ?: save(context, footprintId, uri)
        } else {
            save(context, footprintId, uri)
        }

    /**
     * Deletes the media file called [name] — our own copies only. A gallery
     * reference is an untieing, not a deletion: the album's original belongs
     * to the album, and removing it from a visit takes nothing away from it.
     */
    fun delete(context: Context, name: String?) {
        if (name == null || !isOurs(name)) return
        File(dir(context), name).delete()
    }

    /**
     * A thumbnail of at most [maxEdge] on its long side: an image the ordinary
     * sampled way — which is also a GIF's first frame — and a video through
     * the retriever, a second in.
     */
    fun thumbnail(context: Context, name: String?, maxEdge: Int = 480): Bitmap? {
        if (name == null) return null
        if (MediaRef.isRef(name)) return GalleryMedia.thumbnail(context, name, maxEdge)
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
