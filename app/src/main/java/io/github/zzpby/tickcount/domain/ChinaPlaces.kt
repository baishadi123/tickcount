package io.github.zzpby.tickcount.domain

import kotlin.math.cos

/**
 * The offline map of China's districts: the centre of every 市·区, parsed
 * from the embedded table (`.tools/行政区划/RegionGen.java` writes it from the
 * kakuilan/china_area_mysql dump of the civil-affairs divisions).
 *
 * A visit notes where it stood by the coordinates of the moment; this is what
 * turns them into the words the card shows — the nearest district centre, as
 * long as it is close enough to be honest. Everything runs offline, and a
 * spot the table cannot name honestly simply stays unnamed.
 */
object ChinaPlaces {

    /** Beyond this distance a district's name would be a lie, not a label. */
    private const val MAX_KILOMETRES = 60.0

    /** One district: its centre, and the words the card shows (市·区). */
    data class Place(val latitude: Double, val longitude: Double, val display: String)

    /** Parses the embedded table: `lat|lng|市,区` lines, `#` comments skipped. */
    fun parse(text: String): List<Place> =
        text.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
            val parts = line.split('|')
            if (parts.size < 3) return@mapNotNull null
            val latitude = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val longitude = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val display = parts[2].replace(',', '·')
            if (!latitude.isFinite() || !longitude.isFinite() || display.isEmpty()) {
                return@mapNotNull null
            }
            Place(latitude, longitude, display)
        }.toList()

    /**
     * The district nearest to [latitude]/[longitude], when one is close
     * enough to be honest — null when the spot is beyond the table's reach.
     */
    fun nearest(places: List<Place>, latitude: Double, longitude: Double): Place? {
        var best: Place? = null
        var bestSquare = Double.MAX_VALUE
        // Flat-earth kilometres: a visit's card is honest to tens of
        // kilometres, and this is exact to metres at that scale.
        val lonScale = 111.32 * cos(Math.toRadians(latitude))
        val limit = MAX_KILOMETRES * MAX_KILOMETRES
        for (place in places) {
            val dx = (place.longitude - longitude) * lonScale
            val dy = (place.latitude - latitude) * 110.574
            val square = dx * dx + dy * dy
            if (square < bestSquare) {
                bestSquare = square
                best = place
            }
        }
        return if (bestSquare <= limit) best else null
    }
}
