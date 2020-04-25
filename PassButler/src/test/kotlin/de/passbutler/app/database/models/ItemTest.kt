package de.passbutler.app.database.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.hexToBytes
import de.passbutler.common.toDate
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemTest {

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
                  "created": 1577448000000
                }
                """.trimIndent()
            )
        }
    }
}