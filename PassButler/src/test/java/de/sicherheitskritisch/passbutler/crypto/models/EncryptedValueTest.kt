package de.sicherheitskritisch.passbutler.crypto.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EncryptedValueTest {

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
    fun `Serialize and deserialize a EncryptedValue should result an equal object`() {
        val exampleEncryptedValue = createExampleEncryptedValue()

        val serializedEncryptedValue = exampleEncryptedValue.serialize()
        val deserializedEncryptedValue = EncryptedValue.Deserializer.deserializeOrNull(serializedEncryptedValue)

        Assertions.assertEquals(exampleEncryptedValue, deserializedEncryptedValue)
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

        Assertions.assertEquals(expectedEncryptedValue, EncryptedValue.Deserializer.deserializeOrNull(serializedEncryptedValue))
    }

    @Test
    fun `Deserialize an invalid EncryptedValue returns null`() {
        val serializedEncryptedValue = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedEncryptedValue = null

        Assertions.assertEquals(expectedEncryptedValue, EncryptedValue.Deserializer.deserializeOrNull(serializedEncryptedValue))
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