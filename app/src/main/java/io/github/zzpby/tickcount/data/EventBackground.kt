package io.github.zzpby.tickcount.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * The detail screen's background images, kept in the app's private directory.
 *
 * A picked image is *copied* in rather than referenced by its content URI: a URI handed
 * over by the system picker can be revoked, and a copy needs no storage permission at
 * all — which keeps the app at zero permissions. Images are decoded and scaled down on
 * the way in (a four-megapixel photo is plenty behind text), so a decade of backgrounds
 * still fits in a few megabytes.
 *
 * The stored [CountdownEvent.backgroundUri] is a file *name* inside [dir], so the
 * directory can move and the value is stable across devices. A name whose file has gone
 * — a backup restored on another device — simply renders without a background.
 */
object EventBackgrounds {

    private const val DIR = "backgrounds"
    private const val PREFIX = "bg-"
    private const val JPEG_QUALITY = 85

    /** The longest edge a stored background may have, in pixels. */
    private const val MAX_EDGE = 1600

    /** Where the backgrounds live, created on demand. */
    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /**
     * Copies the image at [uri] into the app's private directory, scaled down, and
     * returns the file name to store — or null when it cannot be read or decoded.
     *
     * The name carries the countdown's id and the save time: re-picking a background
     * writes a *new* file rather than overwriting the old one, which is what lets the
     * detail screen's memory cache tell the change apart. The caller deletes the
     * previous file once the new name is in hand.
     */
    fun save(context: Context, eventId: String, uri: Uri): String? {
        return runCatching {
            val resolver = context.contentResolver
            // Reading the bounds only: decodeStream returns null in this mode — it fills
            // the options out — so the null is not a failure, and only the bounds are.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // Sample so the decode itself stays small: powers of two, sized so the
            // result still has more pixels than [MAX_EDGE] on the long edge.
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= MAX_EDGE) sample *= 2

            val scaled = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, scaled)
            } ?: return null

            // The clock is the nano one: a multi-pick lands several saves inside
            // the same millisecond, and two files under one name would mean the
            // second copy silently replacing the first.
            val file = File(dir(context), "$PREFIX$eventId-${System.nanoTime()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            file.name
        }.getOrNull()
    }

    /** Deletes the background file called [name], if it is one of ours. */
    fun delete(context: Context, name: String?) {
        if (name == null || !name.startsWith(PREFIX)) return
        File(dir(context), name).delete()
    }

    /** The decoded background called [name], or null when it is gone or unreadable. */
    fun bitmap(context: Context, name: String?): Bitmap? {
        if (name == null || !name.startsWith(PREFIX)) return null
        val file = File(dir(context), name)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * The same picture, sampled down to at most [maxEdge] on its long side.
     *
     * The full decode is what the detail screen's one big backdrop wants; a stack
     * of nine thumbnails at full size would hold the better part of a hundred
     * megabytes of pixels for what is two hundred of them on screen.
     */
    fun thumbnail(context: Context, name: String?, maxEdge: Int = 512): Bitmap? {
        if (name == null || !name.startsWith(PREFIX)) return null
        val file = File(dir(context), name)
        if (!file.isFile) return null

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= maxEdge) sample *= 2

            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull()
    }
}
