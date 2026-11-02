package io.github.zzpby.tickcount.domain

import java.io.InputStream
import java.security.MessageDigest

/**
 * A picture the app does not own: a reference into the phone's gallery.
 *
 * The app keeps no copy of such a photo — the album is the only original, and
 * the record carries this string in the photo list where a private file's name
 * used to sit. Entries without the `g:` prefix are the older kind: names of
 * files in the app's own directories, read exactly as before.
 *
 * The reference's one durable identity is the file's SHA-256: size, width,
 * height and taken-time all derive from the same bytes and travel with any
 * byte-preserving migration, but only the hash answers "is this *the* picture"
 * in one step. The MediaStore id rides along as a device-local shortcut — on a
 * new phone it is the first thing to go stale, and the first thing the
 * resolver repairs from the rest of the reference.
 *
 * The wire format, one entry, seven questions answered:
 *
 *     g:<sizeBytes>:<width>x<height>:<dateTakenMillis>:<mime>:<mediaStoreId>:<sha256hex>
 */
object MediaRef {

    const val PREFIX = "g:"

    /** One gallery photo, as the app remembers it. */
    data class Ref(
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        /** Epoch milliseconds, or 0 when the file says nothing about when. */
        val dateTakenMillis: Long,
        val mime: String,
        /** The MediaStore row on the phone that made this reference; stale elsewhere. */
        val mediaStoreId: Long,
        /** The file bytes' SHA-256, 64 lowercase hex digits. */
        val sha256: String,
    )

    /** True when [entry] is a gallery reference rather than a private file's name. */
    fun isRef(entry: String): Boolean = entry.startsWith(PREFIX)

    /** Parses [entry], or null when it is a legacy name or not a reference at all. */
    fun parse(entry: String): Ref? {
        if (!isRef(entry)) return null
        val parts = entry.removePrefix(PREFIX).split(':')
        if (parts.size != 6) return null
        val size = parts[0].toLongOrNull() ?: return null
        if (size <= 0) return null
        val dims = parts[1].split('x')
        if (dims.size != 2) return null
        val width = dims[0].toIntOrNull() ?: return null
        val height = dims[1].toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) return null
        val dateTaken = parts[2].toLongOrNull() ?: return null
        val mime = parts[3]
        if (!mime.contains('/')) return null
        val id = parts[4].toLongOrNull() ?: return null
        val sha = parts[5]
        if (sha.length != 64 || sha.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return Ref(size, width, height, dateTaken, mime, id, sha)
    }

    /** Writes [ref] back into the wire format — the exact inverse of [parse]. */
    fun encode(ref: Ref): String = buildString {
        append(PREFIX)
        append(ref.sizeBytes)
        append(':')
        append(ref.width)
        append('x')
        append(ref.height)
        append(':')
        append(ref.dateTakenMillis)
        append(':')
        append(ref.mime)
        append(':')
        append(ref.mediaStoreId)
        append(':')
        append(ref.sha256)
    }

    /**
     * The bytes of [input], folded into 64 lowercase hex digits. This is the
     * one identity that survives a change of phone: computed once at pick
     * time, verified again on the wire, trusted last of all.
     */
    fun sha256Hex(input: InputStream): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
