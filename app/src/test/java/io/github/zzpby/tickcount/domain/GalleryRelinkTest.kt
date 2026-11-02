package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The relink rules: a mapping touches only what it names, and a record the
 * mapping does not reach is reported untouched (null) — the caller writes
 * back only what actually changed.
 */
class GalleryRelinkTest {

    private val mapping = mapOf(
        "g:100:2x3:0:image/jpeg:18:aa" to "g:100:2x3:0:image/jpeg:53:aa",
    )

    @Test
    fun `mapped entries are replaced`() {
        val photos = listOf("g:100:2x3:0:image/jpeg:18:aa", "bg-x.jpg")
        val remapped = GalleryRelink.remapPhotos(photos, mapping)
        assertEquals(listOf("g:100:2x3:0:image/jpeg:53:aa", "bg-x.jpg"), remapped)
    }

    @Test
    fun `an untouched list answers null`() {
        assertNull(GalleryRelink.remapPhotos(listOf("bg-a.jpg", "fp-b.jpg"), mapping))
        assertNull(GalleryRelink.remapPhotos(emptyList(), mapping))
    }

    @Test
    fun `the cover follows its photo`() {
        assertEquals("g:100:2x3:0:image/jpeg:53:aa", GalleryRelink.remapCover("g:100:2x3:0:image/jpeg:18:aa", mapping))
        assertEquals("bg-x.jpg", GalleryRelink.remapCover("bg-x.jpg", mapping))
        assertNull(GalleryRelink.remapCover(null, mapping))
    }
}
