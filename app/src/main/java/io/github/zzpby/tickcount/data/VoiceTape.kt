package io.github.zzpby.tickcount.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * One voice tape on the wall: her voice, shelved. A title in the letters'
 * own hand, the day it was taken, the length of it, the shape of it (the
 * waveform, sampled while it recorded), and the shell it wears — one of
 * the colourways the wall knows. The sound itself lives in [TapeAudio]'s
 * directory, the same split a capsule's voice letter keeps.
 */
data class VoiceTape(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** The recording's file name inside the private voice-tapes directory. */
    val fileName: String? = null,
    val seconds: Int = 0,
    /** The waveform, sampled as it recorded: 0–100, one to a breath. */
    val bars: List<Int> = emptyList(),
    /** Which colourway this tape's shell wears, indexed into the wall's list. */
    val colorway: Int = 0,
)

/** A fresh tape's id, minted the way every record's is. */
fun newTapeId(): String = UUID.randomUUID().toString()

/**
 * The JSON of a tape, the same one the store and the backups keep — the
 * codec is shared so the two can never drift apart.
 */
object VoiceTapeCodec {

    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_FILE = "file"
    private const val KEY_SECONDS = "seconds"
    private const val KEY_BARS = "bars"
    private const val KEY_COLORWAY = "colorway"

    fun encode(tapes: List<VoiceTape>): JSONArray = JSONArray().apply {
        tapes.forEach { tape ->
            put(
                JSONObject()
                    .put(KEY_ID, tape.id)
                    .put(KEY_TITLE, tape.title)
                    .put(KEY_CREATED_AT, tape.createdAt)
                    .apply {
                        tape.fileName?.let { put(KEY_FILE, it) }
                        if (tape.seconds > 0) put(KEY_SECONDS, tape.seconds)
                        if (tape.bars.isNotEmpty()) put(KEY_BARS, JSONArray(tape.bars))
                        if (tape.colorway != 0) put(KEY_COLORWAY, tape.colorway)
                    },
            )
        }
    }

    fun decode(array: JSONArray): List<VoiceTape> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optString(KEY_ID)
            if (id.isEmpty()) continue
            add(
                VoiceTape(
                    id = id,
                    title = obj.optString(KEY_TITLE),
                    createdAt = obj.optLong(KEY_CREATED_AT, 0L),
                    fileName = obj.optString(KEY_FILE).ifEmpty { null },
                    seconds = obj.optInt(KEY_SECONDS, 0),
                    bars = obj.optJSONArray(KEY_BARS)?.let { bars ->
                        buildList {
                            for (i in 0 until bars.length()) add(bars.optInt(i, 0).coerceIn(0, 100))
                        }
                    } ?: emptyList(),
                    colorway = obj.optInt(KEY_COLORWAY, 0).coerceIn(0, 3),
                ),
            )
        }
    }
}

/**
 * The recordings themselves, kept in the app's private directory — the
 * same rule a capsule's voice letter follows, and for the same reason:
 * there is no album original for a voice, so this directory is the only
 * original there is.
 */
object TapeAudio {

    private const val DIR = "voice-tapes"
    private const val PREFIX = "vt-"

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    // A name is only ours if this store minted it: a hand-made backup must
    // not walk out of the directory with a separator in the name.
    private fun isOurs(name: String): Boolean =
        name.startsWith(PREFIX) && name.none { it == '/' || it == '\\' }

    /** A fresh file to record or import into, owned by [tapeId]'s tape. */
    fun newFile(context: Context, tapeId: String, extension: String = "m4a"): File =
        File(dir(context), "$PREFIX$tapeId-${System.nanoTime()}.$extension")

    /** The recording called [name], or null when it is gone or not one of ours. */
    fun file(context: Context, name: String?): File? {
        if (name == null || !isOurs(name)) return null
        return File(dir(context), name).takeIf { it.isFile }
    }

    /** Deletes the recording called [name], if it is one of ours. */
    fun delete(context: Context, name: String?) {
        if (name == null || !isOurs(name)) return
        File(dir(context), name).delete()
    }

    /**
     * Writes a recording's [bytes] back where the recorder would have put
     * them, for a backup putting things back. The name must be one this
     * store could have minted — a backup must not choose where files land.
     */
    fun restore(context: Context, name: String?, bytes: ByteArray): Boolean {
        if (name == null || !isOurs(name)) return false
        return runCatching {
            File(dir(context), name).writeBytes(bytes)
            true
        }.getOrDefault(false)
    }
}

/**
 * Persists the tape wall, one file per year the tapes were taken in — the
 * capsule store's own reasoning, unchanged: a year's file stays bounded,
 * and saving one tape rewrites only the year it lands in.
 */
class VoiceTapeStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    fun load(): List<VoiceTape> {
        val stored = dir.listFiles { file: File -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .flatMap { file ->
                runCatching { VoiceTapeCodec.decode(JSONArray(file.readText())) }
                    .getOrDefault(emptyList())
            }
        return stored.sortedByDescending { it.createdAt }
    }

    fun save(tapes: List<VoiceTape>) {
        runCatching {
            val byYear = tapes.groupBy { yearOf(it.createdAt) }
            // A year that no longer holds anything loses its file; a file
            // this version cannot read stays, the same mercy the capsule
            // store shows.
            val keep = byYear.keys.map { "$it.json" }.toSet()
            dir.listFiles { file: File -> file.isFile && file.name.endsWith(".json") }
                .orEmpty()
                .filter { it.name !in keep }
                .filter { file ->
                    runCatching { VoiceTapeCodec.decode(JSONArray(file.readText())) }.isSuccess
                }
                .forEach { it.delete() }
            byYear.forEach { (year, list) ->
                writeAtomically(File(dir, "$year.json"), VoiceTapeCodec.encode(list).toString())
            }
        }
    }

    /**
     * Writes the text to a temporary file first and renames it into place:
     * a process death mid-write leaves the old wall whole rather than half
     * of one.
     */
    private fun writeAtomically(file: File, text: String) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(text)
        if (file.exists()) file.delete()
        if (!temporary.renameTo(file)) {
            temporary.delete()
            file.writeText(text)
        }
    }

    private companion object {
        private const val DIR = "voice-tapes-store"

        fun yearOf(createdAt: Long): Int =
            Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).year
    }
}
