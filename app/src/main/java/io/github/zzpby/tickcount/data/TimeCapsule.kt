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
) {
    companion object {
        const val MAX_TITLE_LENGTH = 30
        const val MAX_PHOTOS = 9

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
        if (stored.isNotEmpty()) return sortForReading(stored)
        return migrateLegacy()
    }

    fun save(capsules: List<TimeCapsule>) {
        runCatching {
            val byYear = capsules.groupBy { yearOf(it.opensEpochDay) }
            // A year that no longer holds anything loses its file, so the
            // directory only ever holds the years the letters are in.
            val keep = byYear.keys.map { "$it.json" }.toSet()
            dir.listFiles { file: File -> file.isFile && file.name.endsWith(".json") }
                .orEmpty()
                .filter { it.name !in keep }
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

    private fun migrateLegacy(): List<TimeCapsule> {
        val text = runCatching { legacyFile.takeIf { it.isFile }?.readText() }.getOrNull()
            ?: legacyPrefs.getString(KEY_CAPSULES, null)
            ?: return emptyList()
        val capsules = runCatching { CapsuleCodec.decode(JSONArray(text)) }.getOrDefault(emptyList())
        runCatching {
            if (capsules.isNotEmpty()) {
                save(capsules)
            }
            legacyFile.delete()
            legacyPrefs.edit { remove(KEY_CAPSULES) }
        }
        return sortForReading(capsules)
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
