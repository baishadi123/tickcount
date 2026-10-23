package io.github.zzpby.tickcount.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
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
        const val MAX_MESSAGE_LENGTH = 500
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
            val message = obj.optString(KEY_MESSAGE).take(TimeCapsule.MAX_MESSAGE_LENGTH).trim()
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
 */
class CapsuleStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<TimeCapsule> {
        val text = prefs.getString(KEY_CAPSULES, null) ?: return emptyList()
        return runCatching { CapsuleCodec.decode(JSONArray(text)) }.getOrDefault(emptyList())
    }

    fun save(capsules: List<TimeCapsule>) {
        prefs.edit {
            putString(KEY_CAPSULES, if (capsules.isEmpty()) null else CapsuleCodec.encode(capsules).toString())
        }
    }

    private companion object {
        const val PREFS_NAME = "tickcount_capsules"
        const val KEY_CAPSULES = "capsules"
    }
}
