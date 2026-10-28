package io.github.zzpby.tickcount.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A time capsule: some words and a few photos, sealed until a day — before that
 * day the page only says how long is left, and only when the day has come can it
 * be opened.
 *
 * [opensEpochDay] is the day the capsule can be opened (the day itself counts as
 * arrived, the way [CountdownPhase.TODAY] means everywhere else in the app).
 * [photos] are file names inside the app's private backgrounds directory, copied
 * in by the same route the detail backgrounds travel — no permission, nothing
 * revocable. [openedEpochDay] is stamped the first day it was opened; null while
 * it is still sealed.
 */
data class TimeCapsule(
    val id: String,
    val message: String,
    /** The letter's own title — a letter is a title and its words, not just words. */
    val title: String = "",
    val opensEpochDay: Long,
    val photos: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val openedEpochDay: Long? = null,
    /** The recorded voice letter, a file name inside the private capsule-voice directory. */
    val voice: String? = null,
    /** How long the voice runs, in whole seconds. */
    val voiceSeconds: Int = 0,
    /** The wave the recorder heard, normalised to bars of 0–100, oldest first. */
    val voiceBars: List<Int> = emptyList(),
) {
    companion object {
        const val MAX_TITLE_LENGTH = 30
        const val MAX_PHOTOS = 16

        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * The stored form of the capsule list, as JSON.
 *
 * The same fixed key order [WishCodec] keeps, a separate array, because a capsule
 * is read by its own screen and is neither a wish nor a countdown.
 */
object CapsuleCodec {

    private const val KEY_ID = "i"
    private const val KEY_TITLE = "ti"
    private const val KEY_MESSAGE = "m"
    private const val KEY_OPENS_DAY = "o"
    private const val KEY_PHOTOS = "ph"
    private const val KEY_CREATED = "a"
    private const val KEY_OPENED_DAY = "od"
    private const val KEY_VOICE = "v"
    private const val KEY_VOICE_SECONDS = "vs"
    private const val KEY_VOICE_BARS = "vb"

    fun encode(capsules: List<TimeCapsule>): JSONArray {
        val array = JSONArray()
        capsules.sortedWith(compareBy({ it.opensEpochDay }, { -it.createdAt })).forEach { capsule ->
            array.put(
                JSONObject()
                    .put(KEY_ID, capsule.id)
                    .put(KEY_MESSAGE, capsule.message)
                    .apply { if (capsule.title.isNotEmpty()) put(KEY_TITLE, capsule.title) }
                    .put(KEY_OPENS_DAY, capsule.opensEpochDay)
                    .apply {
                        if (capsule.photos.isNotEmpty()) put(KEY_PHOTOS, JSONArray(capsule.photos))
                    }
                    .put(KEY_CREATED, capsule.createdAt)
                    .apply { capsule.openedEpochDay?.let { put(KEY_OPENED_DAY, it) } }
                    .apply {
                        capsule.voice?.let { voice ->
                            put(KEY_VOICE, voice)
                            put(KEY_VOICE_SECONDS, capsule.voiceSeconds)
                            if (capsule.voiceBars.isNotEmpty()) {
                                put(KEY_VOICE_BARS, JSONArray(capsule.voiceBars))
                            }
                        }
                    }
            )
        }
        return array
    }

    /** Throws on unreadable input rather than returning nothing — a caller restoring a backup has to be able to tell. */
    fun decode(array: JSONArray): List<TimeCapsule> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val message = obj.optString(KEY_MESSAGE).trim()
            val opensDay = if (obj.has(KEY_OPENS_DAY)) obj.optLong(KEY_OPENS_DAY, Long.MIN_VALUE) else Long.MIN_VALUE
            if (message.isEmpty() || opensDay == Long.MIN_VALUE) continue
            add(
                TimeCapsule(
                    id = obj.optString(KEY_ID).takeIf { it.isNotEmpty() }
                        ?: "capsule-$index",
                    message = message,
                    title = obj.optString(KEY_TITLE).take(TimeCapsule.MAX_TITLE_LENGTH).trim(),
                    opensEpochDay = opensDay,
                    photos = readPhotos(obj.optJSONArray(KEY_PHOTOS)),
                    createdAt = obj.optLong(KEY_CREATED, 0L),
                    openedEpochDay = if (obj.has(KEY_OPENED_DAY)) obj.optLong(KEY_OPENED_DAY, 0L) else null,
                    voice = obj.optString(KEY_VOICE).trim().takeIf { it.isNotEmpty() },
                    voiceSeconds = obj.optInt(KEY_VOICE_SECONDS, 0),
                    voiceBars = readBars(obj.optJSONArray(KEY_VOICE_BARS)),
                )
            )
        }
    }

    /** De-duplicated, capped at the ceiling the editor offers. */
    private fun readPhotos(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val name = array.optString(i).trim()
                if (name.isNotEmpty() && name !in this) add(name)
            }
        }.take(TimeCapsule.MAX_PHOTOS)
    }

    /** Bars of 0–100, capped at what the letter's wave draws. */
    private fun readBars(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(array.optInt(i).coerceIn(0, 100))
            }
        }.take(MAX_VOICE_BARS)
    }

    private const val MAX_VOICE_BARS = 160
}

/**
 * Persists the capsule list.
 *
 * Deliberately separate from the other two stores: a capsule is read by its own
 * screen, and each of the three lists is content the others have no business in.
 *
 * One file per year rather than one document for everything: a capsule belongs
 * to the year it opens in, the message has no length limit, so a year's file is
 * what stays bounded as the letters pile up — and saving one capsule rewrites
 * only the year it lands in, never the whole decade. The JSON of one capsule is
 * unchanged, so the codec and the backups are untouched too.
 *
 * Two earlier containers fold in on the first launch that finds them: the
 * single `capsules.json` one version kept, and the `SharedPreferences` string
 * one before that. Each is read once, split into year files, and cleared —
 * a one-way walk, the same one either way.
 */
class CapsuleStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    /** The single file one version kept, folded into the year files once. */
    private val legacyFile = File(context.applicationContext.filesDir, LEGACY_FILE_NAME)

    /** The preferences an earlier version kept the list in, folded in once. */
    private val legacyPrefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<TimeCapsule> {
        val stored = dir.listFiles { file: File -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .flatMap { file ->
                runCatching { CapsuleCodec.decode(JSONArray(file.readText())) }
                    .getOrDefault(emptyList())
            }
        // The walk from the older containers happens whenever they are still
        // there, merged in by id: an interrupted migration resumes instead of
        // stranding the years that never got written, and letters the user has
        // already deleted do not come back.
        val legacy = migrateLegacy() ?: return sortForReading(stored)
        val merged = (stored + legacy).distinctBy { it.id }
        if (merged.isNotEmpty()) save(merged)
        return sortForReading(merged)
    }

    fun save(capsules: List<TimeCapsule>) {
        runCatching {
            val byYear = capsules.groupBy { yearOf(it.opensEpochDay) }
            // A year that no longer holds anything loses its file, so the
            // directory only ever holds the years the letters are in. A file
            // this version cannot read stays: deleting it would turn a
            // recoverable error into a permanent loss.
            val keep = byYear.keys.map { "$it.json" }.toSet()
            dir.listFiles { file: File -> file.isFile && file.name.endsWith(".json") }
                .orEmpty()
                .filter { it.name !in keep }
                .filter { file ->
                    runCatching { CapsuleCodec.decode(JSONArray(file.readText())) }.isSuccess
                }
                .forEach { it.delete() }
            byYear.forEach { (year, list) ->
                writeAtomically(File(dir, "$year.json"), CapsuleCodec.encode(list).toString())
            }
        }
    }

    /**
     * Writes the text to a temporary file first and renames it into place: a
     * process death mid-write leaves the old letter whole rather than a half
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

    /** The order the in-memory list keeps, the same one [CapsuleCodec.encode] writes in. */
    private fun sortForReading(capsules: List<TimeCapsule>) =
        capsules.sortedWith(compareBy({ it.opensEpochDay }, { -it.createdAt }))

    /**
     * Decodes the two older containers, or null when they hold nothing left to
     * fold in — either because they are gone, or because they cannot be read,
     * in which case they stay on disk for a later try rather than being
     * destroyed unread.
     */
    private fun migrateLegacy(): List<TimeCapsule>? {
        val text = runCatching { legacyFile.takeIf { it.isFile }?.readText() }.getOrNull()
            ?: legacyPrefs.getString(KEY_CAPSULES, null)
            ?: return null
        val capsules = runCatching { CapsuleCodec.decode(JSONArray(text)) }.getOrNull() ?: return null
        runCatching {
            legacyFile.delete()
            legacyPrefs.edit { remove(KEY_CAPSULES) }
        }
        return capsules
    }

    private fun yearOf(epochDay: Long): Int =
        java.time.LocalDate.ofEpochDay(epochDay).year

    private companion object {
        const val DIR = "capsules"
        const val LEGACY_FILE_NAME = "capsules.json"
        const val PREFS_NAME = "tickcount_capsules"
        const val KEY_CAPSULES = "capsules"
    }
}
