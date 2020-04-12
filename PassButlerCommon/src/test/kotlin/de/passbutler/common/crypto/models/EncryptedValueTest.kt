package de.passbutler.common.crypto.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.hexToBytes
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EncryptedValueTest {

    @Test
    fun `Serialize and deserialize a EncryptedValue should result an equal object`() {
        val exampleEncryptedValue = createExampleEncryptedValue()

        val serializedEncryptedValue = exampleEncryptedValue.serialize()
        val deserializedEncryptedValue = EncryptedValue.Deserializer.deserializeOrNull(serializedEncryptedValue)

        assertEquals(exampleEncryptedValue, deserializedEncryptedValue)
    }

    @Test
    fun `Serialize an EncryptedValue`() {
        val exampleEncryptedValue = createExampleEncryptedValue()
        val expectedSerialized = createSerializedExampleEncryptedValue()

        assertJSONObjectEquals(expectedSerialized, exampleEncryptedValue.serialize())
    }

    @Test
    fun `Deserialize an EncryptedValue`() {
        val serializedEncryptedValue = createSerializedExampleEncryptedValue()
        val expectedEncryptedValue = createExampleEncryptedValue()

        assertEquals(expectedEncryptedValue, EncryptedValue.Deserializer.deserializeOrNull(serializedEncryptedValue))
    }

    @Test
    fun `Deserialize an invalid EncryptedValue returns null`() {
        val serializedEncryptedValue = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedEncryptedValue = null

        assertEquals(expectedEncryptedValue, EncryptedValue.Deserializer.deserializeOrNull(serializedEncryptedValue))
    }

    companion object {
        private fun createExampleEncryptedValue(): EncryptedValue {
            return EncryptedValue(
                initializationVector = "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
                encryptedValue = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
            )
        }

        private fun createSerializedExampleEncryptedValue(): JSONObject {
            return JSONObject(
                """
                {
                  "initializationVector": "qqqqqqqqqqqqqqqq",
                  "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
                }
                """.trimIndent()
            )
        }
    }
}