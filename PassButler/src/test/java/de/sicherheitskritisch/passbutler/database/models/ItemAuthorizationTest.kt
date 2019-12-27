package de.sicherheitskritisch.passbutler.database.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.*

class ItemAuthorizationTest {

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
    fun `Serialize and deserialize a ItemAuthorization should result an equal object`() {
        val exampleItemAuthorization = createExampleItemAuthorization()

        val serializedItemAuthorization = exampleItemAuthorization.serialize()
        val deserializedItemAuthorization = ItemAuthorization.Deserializer.deserializeOrNull(serializedItemAuthorization)

        assertEquals(exampleItemAuthorization, deserializedItemAuthorization)
    }

    @Test
    fun `Serialize an ItemAuthorization`() {
        val exampleItemAuthorization = createExampleItemAuthorization()
        val expectedSerialized = createSerializedExampleItemAuthorization()

        assertJSONObjectEquals(expectedSerialized, exampleItemAuthorization.serialize())
    }

    @Test
    fun `Deserialize an ItemAuthorization`() {
        val serializedItemAuthorization = createSerializedExampleItemAuthorization()
        val expectedItemAuthorization = createExampleItemAuthorization()

        assertEquals(expectedItemAuthorization, ItemAuthorization.Deserializer.deserializeOrNull(serializedItemAuthorization))
    }

    @Test
    fun `Deserialize an invalid ItemAuthorization returns null`() {
        val serializedItemAuthorization = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedItemAuthorization = null

        assertEquals(expectedItemAuthorization, ItemAuthorization.Deserializer.deserializeOrNull(serializedItemAuthorization))
    }

    companion object {
        private fun createExampleItemAuthorization(): ItemAuthorization {
            val itemKey = ProtectedValue.createInstanceForTesting<CryptographicKey>(
                "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )

            return ItemAuthorization(
                id = "exampleId",
                userId = "exampleUserId",
                itemId = "exampleItemId",
                itemKey = itemKey,
                readOnly = true,
                deleted = true,
                modified = "2019-12-27T12:00:01+0000".toDate(),
                created = "2019-12-27T12:00:00+0000".toDate()
            )
        }

        private fun createSerializedExampleItemAuthorization(): JSONObject {
            return JSONObject(
                """
                {
                  "id": "exampleId",
                  "itemId": "exampleItemId",
                  "userId": "exampleUserId",
                  "itemKey": {
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM",
                    "initializationVector": "qqqqqqqqqqqqqqqq"
                  },
                  "readOnly": true,
                  "deleted": true,
                  "modified": 1577448001000,
                  "created": 1577448000000,
                }
                """.trimIndent()
            )
        }
    }
}

internal fun String.toDate(): Date {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(this)!!
}