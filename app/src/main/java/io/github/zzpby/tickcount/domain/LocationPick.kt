package io.github.zzpby.tickcount.domain

/**
 * Choosing the one fix that names the moment. The radios answer with
 * several — a tight one gone stale, a fuzzy one just heard — and a fix is
 * only worth trusting as much as it is both tight and recent.
 *
 * The rule: a fix costs its accuracy in metres, and ages one accuracy-unit
 * a minute, so a fix must be twice as tight to be worth as much after twice
 * the time. An accuracy of zero is not perfection but ignorance, and is
 * read as very loose. Nothing here talks to a radio; it only judges what
 * the radios already said.
 */
object LocationPick {

    /** What one radio heard: where, how tightly, and when. */
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMetres: Float,
        val epochMillis: Long,
    )

    /** A fix whose accuracy is unknown or nonsensical is read as this loose. */
    private const val UNKNOWN_METRES = 3000.0

    /** The fix worth naming the moment by, or null when nothing was heard. */
    fun best(fixes: List<Fix>, nowMillis: Long): Fix? =
        fixes.minByOrNull { fix ->
            val tightness = if (fix.accuracyMetres > 0f) fix.accuracyMetres.toDouble() else UNKNOWN_METRES
            val ageMinutes = ((nowMillis - fix.epochMillis).coerceAtLeast(0L)) / 60_000.0
            tightness * (1.0 + ageMinutes)
        }
}
