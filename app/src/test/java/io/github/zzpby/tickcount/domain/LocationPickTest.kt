package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Judging what the radios said: a fix is worth its tightness, spent by the
 * minute — these are the cases the moment of noting a spot actually raises.
 */
class LocationPickTest {

    private val now = 1_000_000_000_000L

    private fun fix(
        accuracy: Float,
        ageMinutes: Long = 0,
        tag: Double = 0.0,
    ) = LocationPick.Fix(
        latitude = tag,
        longitude = tag,
        accuracyMetres = accuracy,
        epochMillis = now - ageMinutes * 60_000,
    )

    @Test
    fun `nothing heard settles nothing`() {
        assertNull(LocationPick.best(emptyList(), now))
    }

    @Test
    fun `the tighter fresh fix wins`() {
        val winner = LocationPick.best(
            listOf(fix(accuracy = 12f, tag = 1.0), fix(accuracy = 1800f, tag = 2.0)),
            now,
        )
        assertEquals(1.0, winner!!.latitude, 1e-9)
    }

    @Test
    fun `age spends tightness`() {
        // 20 m heard this minute beats 20 m heard an hour ago; but a GNSS
        // fix an hour old still beats a network fix a kilometre and a half
        // wide (20 × 61 < 1500).
        val winner = LocationPick.best(
            listOf(fix(accuracy = 20f, ageMinutes = 60, tag = 1.0), fix(accuracy = 20f, tag = 2.0)),
            now,
        )
        assertEquals(2.0, winner!!.latitude, 1e-9)

        val oldGnss = LocationPick.best(
            listOf(fix(accuracy = 20f, ageMinutes = 60, tag = 1.0), fix(accuracy = 1500f, tag = 2.0)),
            now,
        )
        assertEquals(1.0, oldGnss!!.latitude, 1e-9)
    }

    @Test
    fun `a fix dated in the future gains nothing by it`() {
        // A clock skewed forward must not outbid a tighter honest fix.
        val winner = LocationPick.best(
            listOf(fix(accuracy = 40f, ageMinutes = -10, tag = 1.0), fix(accuracy = 35f, tag = 2.0)),
            now,
        )
        assertEquals(2.0, winner!!.latitude, 1e-9)
    }

    @Test
    fun `an unknown accuracy is not perfection`() {
        val winner = LocationPick.best(
            listOf(fix(accuracy = 0f, tag = 1.0), fix(accuracy = 2500f, tag = 2.0)),
            now,
        )
        assertEquals(2.0, winner!!.latitude, 1e-9)
    }
}
