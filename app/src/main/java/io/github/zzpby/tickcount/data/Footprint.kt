package io.github.zzpby.tickcount.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * One place the two of you went, on a day, with whatever was captured there:
 * a name on the map of the app's own making — photos, moving or still, and
 * even video, copied in as files rather than referenced, the same rule every
 * other picture here follows.
 *
 * [photos] are file names inside [FootprintMedia]'s directory, mixed freely:
 * a JPEG beside a GIF beside a video is one visit, not three. [latitude] and
 * [longitude] are where the visit was noted from, when they were noted at all
 * — a footprint is a place by name first, and by coordinates only when the
 * moment offered them.
 */
data class Footprint(
    val id: String,
    val place: String,
    val epochDay: Long,
    val photos: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = 0L,
) {
    companion object {
        const val MAX_PLACE_LENGTH = 30
        const val MAX_PHOTOS = 9

        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * The stored form of the footprint list, as JSON.
 *
 * The same fixed key order the other codecs keep, in a per-year set of files —
 * the same layout the capsules settled on, for the same reason: media-heavy
 * content grows without bound, and a year at a time stays small.
 */
object FootprintCodec {

    private const val KEY_ID = "i"
    private const val KEY_PLACE = "p"
    private const val KEY_DAY = "d"
    private const val KEY_PHOTOS = "ph"
    private const val KEY_LAT = "la"
    private const val KEY_LON = "lo"
    private const val KEY_CREATED = "a"

    fun encode(footprints: List<Footprint>): JSONArray {
        val array = JSONArray()
        footprints
            .sortedWith(compareBy({ it.epochDay }, { -it.createdAt }))
            .forEach { footprint ->
                array.put(
                    JSONObject()
                        .put(KEY_ID, footprint.id)
                        .put(KEY_PLACE, footprint.place)
                        .put(KEY_DAY, footprint.epochDay)
                        .apply {
                            if (footprint.photos.isNotEmpty()) put(KEY_PHOTOS, JSONArray(footprint.photos))
                        }
                        .put(KEY_CREATED, footprint.createdAt)
                        .apply {
                            footprint.latitude?.let { put(KEY_LAT, it) }
                            footprint.longitude?.let { put(KEY_LON, it) }
                        }
                )
            }
        return array
    }

    /** Throws on unreadable input rather than returning nothing — a caller restoring a backup has to be able to tell. */
    fun decode(array: JSONArray): List<Footprint> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val place = obj.optString(KEY_PLACE).take(Footprint.MAX_PLACE_LENGTH).trim()
            val day = if (obj.has(KEY_DAY)) obj.optLong(KEY_DAY, Long.MIN_VALUE) else Long.MIN_VALUE
            if (place.isEmpty() || day == Long.MIN_VALUE) continue
            add(
                Footprint(
                    id = obj.optString(KEY_ID).takeIf { it.isNotEmpty() } ?: "footprint-$index",
                    place = place,
                    epochDay = day,
                    photos = readPhotos(obj.optJSONArray(KEY_PHOTOS)),
                    latitude = if (obj.has(KEY_LAT)) obj.optDouble(KEY_LAT) else null,
                    longitude = if (obj.has(KEY_LON)) obj.optDouble(KEY_LON) else null,
                    createdAt = obj.optLong(KEY_CREATED, 0L),
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
        }.take(Footprint.MAX_PHOTOS)
    }
}

/**
 * Persists the footprint list, one file per year of visits — the capsules'
 * own arrangement, adopted for the same reason: media-heavy content is read
 * when it is asked for and a year's file stays small however long the
 * travels run.
 */
class FootprintStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    fun load(): List<Footprint> {
        val stored = dir.listFiles { file: File -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .flatMap { file ->
                runCatching { FootprintCodec.decode(JSONArray(file.readText())) }
                    .getOrDefault(emptyList())
            }
        return stored.sortedWith(compareBy({ it.epochDay }, { -it.createdAt }))
    }

    fun save(footprints: List<Footprint>) {
        runCatching {
            val byYear = footprints.groupBy { java.time.LocalDate.ofEpochDay(it.epochDay).year }
            val keep = byYear.keys.map { "$it.json" }.toSet()
            dir.listFiles { file: File -> file.isFile && file.name.endsWith(".json") }
                .orEmpty()
                .filter { it.name !in keep }
                // A file this version cannot read stays: deleting it would turn
                // a recoverable error into a permanent loss.
                .filter { file ->
                    runCatching { FootprintCodec.decode(JSONArray(file.readText())) }.isSuccess
                }
                .forEach { it.delete() }
            byYear.forEach { (year, list) ->
                writeAtomically(File(dir, "$year.json"), FootprintCodec.encode(list).toString())
            }
        }
    }

    /**
     * Writes the text to a temporary file first and renames it into place: a
     * process death mid-write leaves the old visit whole rather than a half
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
        const val DIR = "footprints"
    }
}
