package de.sicherheitskritisch.passbutler.crypto.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CryptographicKeyTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @AfterEach
    fun unsetUp() {
        unmockkAll()
    }

    @Test
    fun `Serialize a CryptographicKey`() {
        val key = "AABBCCDD".hexToBytes()
        val cryptographicKey = CryptographicKey(key)

        val expectedSerializedCryptographicKey = JSONObject(
            """{"key":"qrvM3Q=="}"""
        )

        assertJSONObjectEquals(expectedSerializedCryptographicKey, cryptographicKey.serialize())
    }

    @Test
    fun `Deserialize a CryptographicKey from valid JSON`() {
        val serializedCryptographicKey = JSONObject(
            """{"key":"qrvM3Q=="}"""
        )

        val key = "AABBCCDD".hexToBytes()
        val expectedCryptographicKey = CryptographicKey(key)

        val deserializedCryptographicKey = CryptographicKey.Deserializer.deserializeOrNull(serializedCryptographicKey)

        assertEquals(expectedCryptographicKey, deserializedCryptographicKey)
    }

    @Test
    fun `Deserialize a CryptographicKey from JSON with invalid key returns null`() {
        val serializedCryptographicKey = JSONObject(
            """{"foobar":""}"""
        )

        val expectedCryptographicKey = null
        val deserializedCryptographicKey = CryptographicKey.Deserializer.deserializeOrNull(serializedCryptographicKey)
        assertEquals(expectedCryptographicKey, deserializedCryptographicKey)
    }

    @Test
    fun `Deserialize a CryptographicKey from JSON with valid key but invalid type returns null`() {
        val serializedCryptographicKey = JSONObject(
            """{"key":1337}"""
        )

        val expectedCryptographicKey = null
        val deserializedCryptographicKey = CryptographicKey.Deserializer.deserializeOrNull(serializedCryptographicKey)
        assertEquals(expectedCryptographicKey, deserializedCryptographicKey)
    }
}