package io.github.zzpby.tickcount.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One thing the two of you want to do together: a title, and whether it has been
 * done — the date it was done on arrives with the tick.
 *
 * [id] exists because wishes are added, ticked and deleted independently; [createdAt]
 * keeps new ones at the top of the unfinished list. Records written before the added-at
 * stamp existed load as zero, which sorts them below everything added since.
 */
data class Wish(
    val id: String,
    val title: String,
    val done: Boolean = false,
    /** The day it was ticked off, as epoch days — null until then. */
    val doneEpochDay: Long? = null,
    val createdAt: Long = 0L,
) {
    companion object {
        const val MAX_TITLE_LENGTH = 60

        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * The stored form of the wish list, as JSON.
 *
 * The same shape [EventCodec] keeps for the countdowns — a fixed key order so the
 * document is stable and diffable — but a separate array, because the two lists are
 * read by different screens and a wish is not a countdown.
 */
object WishCodec {

    private const val KEY_ID = "i"
    private const val KEY_TITLE = "t"
    private const val KEY_DONE = "d"
    private const val KEY_DONE_DAY = "dd"
    private const val KEY_CREATED = "a"

    fun encode(wishes: List<Wish>): JSONArray {
        val array = JSONArray()
        wishes.sortedWith(compareBy({ it.done }, { -it.createdAt })).forEach { wish ->
            array.put(
                JSONObject()
                    .put(KEY_ID, wish.id)
                    .put(KEY_TITLE, wish.title)
                    .put(KEY_DONE, wish.done)
                    .apply { wish.doneEpochDay?.let { put(KEY_DONE_DAY, it) } }
                    .put(KEY_CREATED, wish.createdAt)
            )
        }
        return array
    }

    /** Throws on unreadable input rather than returning nothing — a caller restoring a backup has to be able to tell. */
    fun decode(array: JSONArray): List<Wish> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val title = obj.optString(KEY_TITLE).take(Wish.MAX_TITLE_LENGTH)
            if (title.isEmpty()) continue
            add(
                Wish(
                    id = obj.optString(KEY_ID).takeIf { it.isNotEmpty() }
                        ?: "wish-$index",
                    title = title,
                    done = obj.optBoolean(KEY_DONE, false),
                    doneEpochDay = if (obj.has(KEY_DONE_DAY)) obj.optLong(KEY_DONE_DAY, 0L) else null,
                    createdAt = obj.optLong(KEY_CREATED, 0L),
                )
            )
        }
    }
}

/**
 * Persists the wish list.
 *
 * Deliberately separate from [EventStore]: the two lists are read by different
 * screens, and a wish is not a countdown — mixing them would put one document in
 * the way of both.
 */
class WishStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Wish> {
        val text = prefs.getString(KEY_WISHES, null) ?: return emptyList()
        return runCatching { WishCodec.decode(JSONArray(text)) }.getOrDefault(emptyList())
    }

    fun save(wishes: List<Wish>) {
        prefs.edit {
            putString(KEY_WISHES, if (wishes.isEmpty()) null else WishCodec.encode(wishes).toString())
        }
    }

    private companion object {
        const val PREFS_NAME = "tickcount_wishes"
        const val KEY_WISHES = "wishes"
    }
}
