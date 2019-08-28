package de.sicherheitskritisch.passbutler.crypto.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KeyDerivationInformationTest {

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
    fun `Serialize a KeyDerivationInformation`() {
        val salt = "70C947CD".hexToBytes()
        val iterationCount = 1234
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val expectedSerializedKeyDerivationInformation = JSONObject(
            """{"salt":"cMlHzQ==","iterationCount":1234}"""
        )

        assertJSONObjectEquals(expectedSerializedKeyDerivationInformation, keyDerivationInformation.serialize())
    }

    @Test
    fun `Deserialize a KeyDerivationInformation from valid JSON`() {
        val serializedKeyDerivationInformation = JSONObject(
            """{"salt":"cMlHzQ==","iterationCount":1234}"""
        )

        val salt = "70C947CD".hexToBytes()
        val iterationCount = 1234
        val expectedKeyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val deserializedKeyDerivationInformation = KeyDerivationInformation.Deserializer.deserializeOrNull(serializedKeyDerivationInformation)

        assertEquals(expectedKeyDerivationInformation, deserializedKeyDerivationInformation)
    }

    @Test
    fun `Deserialize a KeyDerivationInformation from JSON with invalid keys returns null`() {
        val serializedKeyDerivationInformation = JSONObject(
            """{"foo":"cMlHzQ==","bar":1234}"""
        )

        val expectedKeyDerivationInformation = null
        val deserializedKeyDerivationInformation = KeyDerivationInformation.Deserializer.deserializeOrNull(serializedKeyDerivationInformation)
        assertEquals(expectedKeyDerivationInformation, deserializedKeyDerivationInformation)
    }

    @Test
    fun `Deserialize a KeyDerivationInformation from JSON with valid keys but invalid salt type returns null`() {
        val serializedKeyDerivationInformation = JSONObject(
            """{"salt":1234,"iterationCount":1234}"""
        )

        val expectedKeyDerivationInformation = null
        val deserializedKeyDerivationInformation = KeyDerivationInformation.Deserializer.deserializeOrNull(serializedKeyDerivationInformation)
        assertEquals(expectedKeyDerivationInformation, deserializedKeyDerivationInformation)
    }

    @Test
    fun `Deserialize a KeyDerivationInformation from JSON with valid keys but invalid iterationCount type returns null`() {
        val serializedKeyDerivationInformation = JSONObject(
            """{"salt":"cMlHzQ==","iterationCount":"foo"}"""
        )

        val expectedKeyDerivationInformation = null
        val deserializedKeyDerivationInformation = KeyDerivationInformation.Deserializer.deserializeOrNull(serializedKeyDerivationInformation)
        assertEquals(expectedKeyDerivationInformation, deserializedKeyDerivationInformation)
    }
}