package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * The gallery reference's grammar: what goes out comes back, what is not a
 * reference is not parsed, and a mangled one is refused rather than guessed.
 */
class MediaRefTest {

    private val sha = "a".repeat(32) + "b".repeat(32)
    private val ref = MediaRef.Ref(
        sizeBytes = 3_845_212,
        width = 4032,
        height = 3024,
        dateTakenMillis = 1_698_745_612_000,
        mime = "image/jpeg",
        mediaStoreId = 947,
        sha256 = sha,
    )

    @Test
    fun `encode and parse are exact inverses`() {
        val entry = MediaRef.encode(ref)
        assertTrue(MediaRef.isRef(entry))
        assertEquals(ref, MediaRef.parse(entry))
    }

    @Test
    fun `a legacy file name is not a reference`() {
        assertFalse(MediaRef.isRef("fp-abc-123.jpg"))
        assertFalse(MediaRef.isRef("bg-xyz.png"))
        assertNull(MediaRef.parse("fp-abc-123.jpg"))
        assertNull(MediaRef.parse(""))
    }

    @Test
    fun `mangled references are refused, not guessed`() {
        assertNull(MediaRef.parse("g:1:2x3"))
        assertNull(MediaRef.parse("g:abc:2x3:0:image/jpeg:1:$sha"))
        assertNull(MediaRef.parse("g:-1:2x3:0:image/jpeg:1:$sha"))
        assertNull(MediaRef.parse("g:100:0x3:0:image/jpeg:1:$sha"))
        assertNull(MediaRef.parse("g:100:2x3:0:noimage:1:$sha"))
        assertNull(MediaRef.parse("g:100:2x3:0:image/jpeg:x:$sha"))
        assertNull(MediaRef.parse("g:100:2x3:0:image/jpeg:1:zz"))
        assertNull(MediaRef.parse("g:100:2x3:zz:image/jpeg:1:$sha"))
    }

    @Test
    fun `zero taken-time is a legal unknown`() {
        val unknown = ref.copy(dateTakenMillis = 0)
        assertEquals(unknown, MediaRef.parse(MediaRef.encode(unknown)))
    }

    @Test
    fun `sha256 reads bytes to the known answer`() {
        val digest = MediaRef.sha256Hex(ByteArrayInputStream("abc".toByteArray()))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest,
        )
    }

    @Test
    fun `sha256 of nothing is the empty answer`() {
        val digest = MediaRef.sha256Hex(ByteArrayInputStream(ByteArray(0)))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            digest,
        )
    }
}
