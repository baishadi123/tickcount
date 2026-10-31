package io.github.zzpby.tickcount.domain

/**
 * The provinces of the country, parsed from the embedded simplified
 * boundaries: one ring a line as `名称|lat,lng lat,lng …`, and a province
 * with islands keeps several rings under one name.
 *
 * This is what lights the country frame of 万疆's map: a province counts as
 * arrived-in when a footprint stands inside it, decided here against the
 * rings. Nothing is stored — the lit map is a reading of the footprints,
 * not a second record to keep in step.
 */
object ChinaProvinces {

    /** One province: its name and every ring of ground it covers. */
    data class Province(val name: String, val rings: List<List<Pair<Double, Double>>>)

    /** Parses the embedded table: `#` comments and short or malformed lines are skipped. */
    fun parse(text: String): List<Province> {
        val byName = LinkedHashMap<String, MutableList<List<Pair<Double, Double>>>>()
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val bar = line.indexOf('|')
                if (bar <= 0) return@forEach
                val name = line.substring(0, bar).trim()
                val ring = line.substring(bar + 1).split(' ').mapNotNull { pair ->
                    val parts = pair.split(',')
                    val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                    val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                    lat to lng
                }
                if (name.isEmpty() || ring.size < 4) return@forEach
                byName.getOrPut(name) { mutableListOf() }.add(ring)
            }
        return byName.map { (name, rings) -> Province(name, rings) }
    }

    /**
     * The province [latitude]/[longitude] falls inside, or null when the
     * spot is at sea or beyond the border. The bounding box of each ring is
     * asked first: most points are nowhere near most rings, and a box is
     * cheaper to miss with than a polygon to cast against.
     */
    fun provinceOf(provinces: List<Province>, latitude: Double, longitude: Double): String? {
        for (province in provinces) {
            for (ring in province.rings) {
                if (!coversBox(ring, latitude, longitude)) continue
                if (inside(ring, latitude, longitude)) return province.name
            }
        }
        return null
    }

    /** Whether the point sits inside the ring's bounding box. */
    private fun coversBox(ring: List<Pair<Double, Double>>, lat: Double, lng: Double): Boolean {
        var minLat = 90.0
        var maxLat = -90.0
        var minLng = 180.0
        var maxLng = -180.0
        for ((rLat, rLng) in ring) {
            if (rLat < minLat) minLat = rLat
            if (rLat > maxLat) maxLat = rLat
            if (rLng < minLng) minLng = rLng
            if (rLng > maxLng) maxLng = rLng
        }
        return lat in minLat..maxLat && lng in minLng..maxLng
    }

    /**
     * Even-odd ray casting, the ray walked due north. The country sits
     * clear of the antimeridian, so plain longitude crossings decide it.
     */
    private fun inside(ring: List<Pair<Double, Double>>, lat: Double, lng: Double): Boolean {
        var odd = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.second > lng) != (b.second > lng)) {
                val crossLat = a.first + (lng - a.second) * (b.first - a.first) / (b.second - a.second)
                if (lat < crossLat) odd = !odd
            }
            j = i
        }
        return odd
    }
}
