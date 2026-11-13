package io.github.zzpby.tickcount.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.zzpby.tickcount.domain.MediaRef
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The gallery side of a photo reference: making one at pick time, and finding
 * the picture again afterwards — this phone or any phone whose album the
 * bytes travelled to.
 *
 * The resolver's rule is the identity ladder: the remembered MediaStore id
 * first (verified by size), then a fingerprint search — size narrows the
 * album to a handful, dimensions and taken-time narrow further, and the
 * SHA-256 speaks last. Nothing is matched on metadata alone when the bytes
 * are reachable to ask.
 *
 * Every public answer is null when the album cannot be read — permission not
 * granted, picture deleted, migration not yet run. A null is an empty frame
 * in the grid, never a crash.
 */
object GalleryMedia {

    /**
     * The pixel ceiling a whole-picture decode may be turned under: two
     * bitmaps live through the turn, so the pair's peak must stay within a
     * phone's means. 24 megapixels is above every screen and every common
     * camera; only sensor monsters ever meet it.
     */
    private const val UPTURN_PIXEL_CAP = 24_000_000L

    /** The MIME types a reference may hold; everything else stays a private copy. */
    private val MIME_EXTENSIONS = mapOf(
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

    /** True when [entry] holds a picture type a reference may carry. */
    fun isSupportedMime(mime: String): Boolean = mime in MIME_EXTENSIONS

    /**
     * Reads the picture [uri] hands over — once — and returns its reference
     * string: the bytes hashed, the dimensions decoded, the taken-time read
     * from where the file itself keeps it. Nothing is kept: the one pass
     * through a scratch file in the cache is deleted on the way out, because
     * the album remains the only original.
     */
    fun createRef(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        if (mime == null) return null
        if (mime !in MIME_EXTENSIONS) return null

        val scratch = File(context.cacheDir, "pick-${System.nanoTime()}.bin")
        try {
            resolver.openInputStream(uri)?.use { input ->
                scratch.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (!scratch.isFile || scratch.length() <= 0) return null

            val sha = scratch.inputStream().use { MediaRef.sha256Hex(it) } ?: return null
            val size = scratch.length()

            // Dimensions come from the album's own metadata first: it applies
            // the EXIF rotation, and the fingerprint must be written in the
            // same terms a future phone's album will speak — coded dimensions
            // from a decode would be sideways to it, and every portrait
            // picture would read as missing.
            val meta = queryMediaMetadata(resolver, uri)
            var width = meta?.first ?: 0
            var height = meta?.second ?: 0
            var dateTaken = meta?.third ?: 0L
            if (mime.startsWith("image/")) {
                if (width <= 0 || height <= 0) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(scratch.absolutePath, bounds)
                    width = bounds.outWidth
                    height = bounds.outHeight
                }
                if (width <= 0 || height <= 0) return null
                if (dateTaken <= 0L) {
                    dateTaken = exifTakenMillis(scratch)
                }
            } else {
                if (width <= 0 || height <= 0) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(scratch.absolutePath)
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            ?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ?.toIntOrNull() ?: 0
                        if (dateTaken <= 0L) {
                            dateTaken = retrieverTakenMillis(retriever)
                        }
                    } finally {
                        retriever.release()
                    }
                }
                if (width <= 0 || height <= 0) return null
            }

            val draft = MediaRef.Ref(size, width, height, dateTaken, mime, 0L, sha)
            // The picker answers with a picker-scoped row; settle the real
            // album id once, now, by the fingerprint — every later read then
            // takes the fast path instead of the search.
            val asked = runCatching { queryLong(resolver, uri, MediaStore.MediaColumns._ID) }
                .getOrDefault(null) ?: 0L
            val settledId = if (asked > 0L && !uri.toString().contains("/picker/")) {
                asked
            } else {
                searchByFingerprint(context, draft)?.lastPathSegment?.toLongOrNull() ?: asked
            }
            MediaRef.encode(draft.copy(mediaStoreId = settledId))
        } finally {
            scratch.delete()
        }
    }.getOrNull()

    /**
     * A small [Bitmap] of [entry], at most [maxEdge] on the long side — the
     * album's own ready-made miniature: the same one the system gallery's
     * grid shows, generated once by MediaProvider and kept on disk for
     * everyone. Decoding the original here instead is two orders of
     * magnitude more work for the same little square, and a grid of them is
     * the difference between gliding and crawling.
     *
     * Falls back to a sampled decode of the stream on systems without the
     * thumbnail service (a reference restored onto an old device).
     */
    fun thumbnail(context: Context, entry: String, maxEdge: Int): Bitmap? {
        val ref = MediaRef.parse(entry) ?: return null
        return runCatching {
            val uri = resolveUri(context, ref) ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    uri,
                    android.util.Size(maxEdge, maxEdge),
                    null,
                )
            } else if (ref.mime.startsWith("video/")) {
                videoFrame(context, ref, maxEdge)
            } else {
                sampledDecode(context, uri, ref.width, ref.height, maxEdge)
            }
        }.getOrNull()
    }

    /**
     * The picture whole, for a full-screen page — turned upright the way the
     * photographer held the camera: the decoder honours pixels, not the
     * orientation tag beside them, so a portrait taken sideways stays
     * sideways unless someone reads the tag and turns it.
     *
     * Turning means two bitmaps alive at once, the sideways one and the
     * upright one, so a monster above [UPTURN_PIXEL_CAP] is sampled down to
     * the cap first — still far beyond any screen, and the peak stays
     * within a phone's means.
     */
    fun bitmap(context: Context, entry: String): Bitmap? {
        val ref = MediaRef.parse(entry) ?: return null
        return runCatching {
            val uri = resolveUri(context, ref) ?: return null
            val orientation = orientationOf(context, uri)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            val sample = if (pixels > UPTURN_PIXEL_CAP) {
                // Powers of two only, per the decoder's own rule.
                var s = 1
                while (pixels / (s.toLong() * s * 4) > UPTURN_PIXEL_CAP) s *= 2
                s
            } else {
                1
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            upright(decoded, orientation)
        }.getOrNull()
    }

    /** The orientation tag a stream carries, NORMAL when nothing can be read. */
    private fun orientationOf(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /** The orientation tag one of our own files carries, for the copies' pages. */
    internal fun uprightOf(file: File): Int = runCatching {
        ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /**
     * Turns [bitmap] the way its [orientation] tag says the camera held it,
     * recycling the sideways original — the one reading a page deserves is
     * the upright one, not the one the sensor happened to store.
     */
    internal fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    /** True when the reference holds a moving picture. */
    fun isVideo(entry: String): Boolean =
        MediaRef.parse(entry)?.mime?.startsWith("video/") == true

    /** True when the reference holds an animated GIF. */
    fun isAnimated(entry: String): Boolean =
        MediaRef.parse(entry)?.mime == "image/gif"

    /**
     * Where the picture plays from: the MediaStore content uri, resolved
     * through the identity ladder. The player streams from here; nothing is
     * copied to hold it.
     */
    fun mediaUri(context: Context, entry: String): Uri? {
        val ref = MediaRef.parse(entry) ?: return null
        return resolveUri(context, ref)
    }

    /** How the health check reads one entry: in place, found again, or gone. */
    enum class Health { IN_PLACE, RELOCATED, MISSING }

    /**
     * Checks one reference against the album it points into: the id stands,
     * the fingerprint found it a new row, or the picture is not there.
     */
    fun health(context: Context, entry: String): Health? {
        val ref = MediaRef.parse(entry) ?: return null
        return when {
            idStands(context, ref) -> Health.IN_PLACE
            searchByFingerprint(context, ref) != null -> Health.RELOCATED
            else -> Health.MISSING
        }
    }

    /**
     * The album row the fingerprint found, when the remembered id has gone
     * stale — the number a migration repair writes back into the record.
     * Null when the id still stands or the picture is nowhere.
     */
    fun relocatedId(context: Context, entry: String): Long? {
        val ref = MediaRef.parse(entry) ?: return null
        if (idStands(context, ref)) return null
        return searchByFingerprint(context, ref)?.lastPathSegment?.toLongOrNull()
    }

    /**
     * Builds a reference for bytes the app already holds: an old private
     * copy, fingerprinted and matched against the album. The copy and the
     * album original are the same bytes — that is the whole premise of this
     * migration — so a match is exact by hash, and the caller may delete the
     * copy once the record carries the reference instead.
     *
     * Null when the file is unreadable, of a type references cannot hold, or
     * simply not in the album any more — in which case the copy keeps its
     * job and nothing changes.
     */
    fun refFromFile(context: Context, file: File): String? = runCatching {
        if (!file.isFile || file.length() <= 0) return null
        val mime = FootprintMedia.mimeOf(file.name) ?: return null

        val sha = file.inputStream().use { MediaRef.sha256Hex(it) } ?: return null
        val size = file.length()

        var width = 0
        var height = 0
        var dateTaken = 0L
        if (mime.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            width = bounds.outWidth
            height = bounds.outHeight
            if (width <= 0 || height <= 0) return null
            dateTaken = exifTakenMillis(file)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                if (width <= 0 || height <= 0) return null
                dateTaken = retrieverTakenMillis(retriever)
            } finally {
                retriever.release()
            }
        }

        val draft = MediaRef.Ref(size, width, height, dateTaken, mime, 0L, sha)
        val id = searchByFingerprint(context, draft)?.lastPathSegment?.toLongOrNull() ?: return null
        MediaRef.encode(draft.copy(mediaStoreId = id))
    }.getOrNull()

    // --- the identity ladder ------------------------------------------------

    /**
     * The content uri the picture reads from now: the remembered row when it
     * still holds the same bytes, else the fingerprint's verdict.
     */
    private fun resolveUri(context: Context, ref: MediaRef.Ref): Uri? {
        if (ref.mediaStoreId > 0L) {
            val uri = ContentUris.withAppendedId(collection(ref), ref.mediaStoreId)
            val size = runCatching {
                queryLong(context.contentResolver, uri, MediaStore.MediaColumns.SIZE)
            }.getOrDefault(-1L)
            if (size == ref.sizeBytes) return uri
        }
        return searchByFingerprint(context, ref)
    }

    private fun idStands(context: Context, ref: MediaRef.Ref): Boolean {
        if (ref.mediaStoreId <= 0L) return false
        val uri = ContentUris.withAppendedId(collection(ref), ref.mediaStoreId)
        val size = runCatching {
            queryLong(context.contentResolver, uri, MediaStore.MediaColumns.SIZE)
        }.getOrDefault(-1L)
        return size == ref.sizeBytes
    }

    /**
     * Size picks the candidates out of the album in one query; dimensions and
     * taken-time cut the handful down — softly, for a rotated photo may carry
     * its dimensions either way round — and the hash speaks last, even over a
     * lone candidate: identity here is the content, not the coincidence of
     * its measurements.
     */
    private fun searchByFingerprint(context: Context, ref: MediaRef.Ref): Uri? = runCatching {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_TAKEN,
        )
        resolver.query(
            collection(ref),
            projection,
            "${MediaStore.MediaColumns.SIZE} = ?",
            arrayOf(ref.sizeBytes.toString()),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val strict = mutableListOf<Uri>()
            val loose = mutableListOf<Uri>()
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection(ref), cursor.getLong(idColumn))
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val taken = cursor.getLong(takenColumn)
                if (ref.dateTakenMillis > 0 && taken > 0 && taken != ref.dateTakenMillis) {
                    // A different moment with the same weight is almost
                    // surely another picture; it may still ride along only
                    // when nothing strict turns up.
                    loose += uri
                    continue
                }
                if (ref.width > 0 && width > 0 && height > 0 &&
                    (width != ref.width || height != ref.height)
                ) {
                    loose += uri
                    continue
                }
                strict += uri
            }
            (strict.ifEmpty { loose }).firstOrNull { candidate ->
                resolver.openInputStream(candidate)?.use { stream ->
                    MediaRef.sha256Hex(stream) == ref.sha256
                } ?: false
            }
        }
    }.getOrNull()

    // --- helpers ------------------------------------------------------------

    private fun collection(ref: MediaRef.Ref): Uri =
        if (ref.mime.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun queryLong(resolver: android.content.ContentResolver, uri: Uri, column: String): Long? =
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    /** The album's own word on the picture: width, height, taken-time — or null. */
    private fun queryMediaMetadata(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): Triple<Int, Int, Long>? = runCatching {
        resolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DATE_TAKEN,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val width = if (cursor.isNull(0)) 0 else cursor.getInt(0)
            val height = if (cursor.isNull(1)) 0 else cursor.getInt(1)
            val taken = if (cursor.isNull(2)) 0L else cursor.getLong(2)
            Triple(width, height, taken)
        }
    }.getOrNull()

    private fun sampledDecode(
        context: Context,
        uri: Uri,
        width: Int,
        height: Int,
        maxEdge: Int,
    ): Bitmap? {
        val longest = maxOf(width, height)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun videoFrame(context: Context, ref: MediaRef.Ref, maxEdge: Int): Bitmap? {
        val uri = resolveUri(context, ref) ?: return null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000) ?: retriever.frameAtTime ?: return null
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

    private fun exifTakenMillis(file: File): Long = runCatching {
        val taken = ExifInterface(file.absolutePath)
            .getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return 0L
        LocalDateTime.parse(taken, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT))
            .atOffset(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun retrieverTakenMillis(retriever: MediaMetadataRetriever): Long = runCatching {
        // The retriever's date reads "20190812T102030.000Z" or close to it —
        // seconds are plenty for a fingerprint.
        val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            ?: return 0L
        val seconds = raw.substringBefore('.').takeIf { it.length >= 15 } ?: return 0L
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        LocalDateTime.parse(seconds, formatter)
            .atOffset(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
    }.getOrDefault(0L)
}
