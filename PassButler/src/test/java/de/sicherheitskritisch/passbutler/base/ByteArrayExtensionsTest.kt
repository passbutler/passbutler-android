package de.sicherheitskritisch.passbutler.base

import de.sicherheitskritisch.passbutler.hexToBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ByteArrayExtensionsTest {

    @Test
    fun `Compares two null ByteArray results that they are equal`() {
        val firstByteArray: ByteArray? = null
        val secondByteArray: ByteArray? = null

        assertEquals(true, firstByteArray.optionalContentEquals(secondByteArray))
    }

    @Test
    fun `Compares a null ByteArray and non-null ByteArray results that they are not equal`() {
        val firstByteArray: ByteArray? = null
        val secondByteArray: ByteArray? = ByteArray(1)

        assertEquals(false, firstByteArray.optionalContentEquals(secondByteArray))
    }

    @Test
    fun `Compares a non-null ByteArray and null ByteArray results that they are not equal`() {
        val firstByteArray: ByteArray? = ByteArray(1)
        val secondByteArray: ByteArray? = null

        assertEquals(false, firstByteArray.optionalContentEquals(secondByteArray))
    }

    @Test
    fun `Compares two equal non-null ByteArrays results that they are equal`() {
        val firstByteArray: ByteArray? = "AABBCC".hexToBytes()
        val secondByteArray: ByteArray? = "AABBCC".hexToBytes()

        assertEquals(true, firstByteArray.optionalContentEquals(secondByteArray))
    }

    @Test
    fun `Compares two different in size ByteArrays results that they are not equal`() {
        val firstByteArray: ByteArray? = "AABBCC".hexToBytes()
        val secondByteArray: ByteArray? = "AABBCCDD".hexToBytes()

        assertEquals(false, firstByteArray.optionalContentEquals(secondByteArray))
    }

    @Test
    fun `Compares two different in content ByteArrays results that they are not equal`() {
        val firstByteArray: ByteArray? = "AABBCC".hexToBytes()
        val secondByteArray: ByteArray? = "AABBDD".hexToBytes()

        assertEquals(false, firstByteArray.optionalContentEquals(secondByteArray))
    }
}