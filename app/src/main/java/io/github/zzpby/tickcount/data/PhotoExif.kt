package io.github.zzpby.tickcount.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What a photo says about itself, read once when the viewer's info panel
 * opens — never while a list is on screen.
 *
 * The reading follows the viewer guide's three rules: only on demand, only
 * on the IO dispatcher, and always caught — a broken JPEG is an empty panel,
 * never a crash. Everything nullable: a field the file does not carry simply
 * does not show, and an entry nothing can be read from is a null the panel
 * answers with "no information".
 */
data class PhotoExif(
    val takenAt: LocalDateTime? = null,
    val camera: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
    val iso: String? = null,
    val resolution: String? = null,
    val fileSize: Long? = null,
) {
    companion object {

        /** The format a JPEG keeps its capture time in: `2026:11:09 10:24:00`. */
        private val EXIF_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        /** The format the panel shows it in. */
        private val PANEL_TIME = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm", Locale.CHINA)

        /**
         * Reads what the picture at [uri] carries, on the IO dispatcher — an
         * openInputStream is a blocking call wherever it goes. The viewer's
         * own media source decides what the entry's bytes stream from.
         */
        suspend fun load(context: Context, uri: Uri?): PhotoExif? =
            withContext(Dispatchers.IO) {
                if (uri == null) return@withContext null
                val exif = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                }.getOrNull()

                val takenAt = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?.let { runCatching { LocalDateTime.parse(it, EXIF_TIME) }.getOrNull() }
                // Make and model arrive as two tags; together they are the camera.
                val camera = exif?.let { e ->
                    listOfNotNull(
                        e.getAttribute(ExifInterface.TAG_MAKE)?.trim(),
                        e.getAttribute(ExifInterface.TAG_MODEL)?.trim(),
                    ).joinToString(" ").ifEmpty { null }
                }
                // The shutter is stored as seconds — 1/250 arrives as 0.004 — and
                // wants its fraction back for the panel.
                val shutter = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    ?.takeIf { it > 0.0 }
                    ?.let { seconds ->
                        when {
                            seconds < 1.0 -> "1/${Math.round(1.0 / seconds)}"
                            else -> "${trim(seconds.toFloat())}s"
                        }
                    }
                val aperture = exif?.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                    ?.takeIf { it > 0.0 }
                    ?.let { "f/${trim(it.toFloat())}" }
                val iso = exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                    ?.takeIf { it > 0 }?.toString()
                val fileSize = lengthOf(context, uri)

                val resolution = resolutionOf(context, uri)
                if (takenAt == null && camera == null && aperture == null &&
                    shutter == null && iso == null && resolution == null && fileSize == null
                ) {
                    null
                } else {
                    PhotoExif(
                        takenAt = takenAt,
                        camera = camera,
                        aperture = aperture,
                        shutter = shutter,
                        iso = iso,
                        resolution = resolution,
                        fileSize = fileSize,
                    )
                }
            }

        /** The bytes behind [uri], when the resolver will say; a stream's word is good enough. */
        private fun lengthOf(context: Context, uri: Uri): Long? = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 }
        }.getOrNull()

        /** Width × height straight from the file's own bounds, without decoding a pixel of it. */
        private fun resolutionOf(context: Context, uri: Uri): String? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                "${bounds.outWidth} × ${bounds.outHeight}"
            } else {
                null
            }
        }.getOrNull()

        private fun trim(value: Float): String =
            if (value >= 10f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }

    /** The capture time as the panel words it. */
    fun takenAtLine(): String? = takenAt?.let { PANEL_TIME.format(it) }
}
