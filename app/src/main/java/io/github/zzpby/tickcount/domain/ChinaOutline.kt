package io.github.zzpby.tickcount.domain

/**
 * The border of the country, parsed from the embedded outline: one ring a
 * line, points as `lat,lng` pairs space-separated, `#` comments skipped.
 *
 * The map draws this as its own honest edge — everything beyond the border
 * is the app's paper rather than another country's tiles, because the page
 * is the country the two of you walk, and nowhere else.
 */
object ChinaOutline {

    /** Every ring, each a closed walk of (latitude, longitude) points. */
    fun rings(text: String): List<List<Pair<Double, Double>>> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                line.split(' ').mapNotNull { pair ->
                    val parts = pair.split(',')
                    val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                    val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                    lat to lng
                }
            }
            .filter { it.size >= 4 }
            .toList()
}
