package de.passbutler.common.crypto.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.hexToBytes
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyDerivationInformationTest {

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