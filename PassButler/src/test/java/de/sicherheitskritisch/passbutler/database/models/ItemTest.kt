package de.sicherheitskritisch.passbutler.database.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
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

class ItemTest {

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
    fun `Serialize and deserialize a Item should result an equal object`() {
        val exampleItem = createExampleItem()

        val serializedItem = exampleItem.serialize()
        val deserializedItem = Item.Deserializer.deserializeOrNull(serializedItem)

        assertEquals(exampleItem, deserializedItem)
    }

    @Test
    fun `Serialize an Item`() {
        val exampleItem = createExampleItem()
        val expectedSerialized = createSerializedExampleItem()

        assertJSONObjectEquals(expectedSerialized, exampleItem.serialize())
    }

    @Test
    fun `Deserialize an Item`() {
        val serializedItem = createSerializedExampleItem()
        val expectedItem = createExampleItem()

        assertEquals(expectedItem, Item.Deserializer.deserializeOrNull(serializedItem))
    }

    @Test
    fun `Deserialize an invalid Item returns null`() {
        val serializedItem = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedItem = null

        assertEquals(expectedItem, Item.Deserializer.deserializeOrNull(serializedItem))
    }

    companion object {
        private fun createExampleItem(): Item {
            val itemData = ProtectedValue.createInstanceForTesting<ItemData>(
                "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )

            return Item(
                id = "exampleId",
                userId = "exampleUserId",
                data = itemData,
                deleted = true,
                modified = "2019-12-27T12:00:01+0000".toDate(),
                created = "2019-12-27T12:00:00+0000".toDate()
            )
        }

        private fun createSerializedExampleItem(): JSONObject {
            return JSONObject(
                """
                {
                  "id": "exampleId",
                  "userId": "exampleUserId",
                  "data": {
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM",
                    "initializationVector": "qqqqqqqqqqqqqqqq"
                  },
                  "deleted": true,
                  "modified": 1577448001000,
                  "created": 1577448000000,
                }
                """.trimIndent()
            )
        }
    }
}