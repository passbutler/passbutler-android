package de.sicherheitskritisch.passbutler.database.models

import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemDataTest {

    @Test
    fun `Serialize and deserialize a ItemData should result an equal object`() {
        val exampleItemData = createExampleItemData()

        val serializedItemData = exampleItemData.serialize()
        val deserializedItemData = ItemData.Deserializer.deserializeOrNull(serializedItemData)

        assertEquals(exampleItemData, deserializedItemData)
    }

    @Test
    fun `Serialize an ItemData`() {
        val exampleItemData = createExampleItemData()
        val expectedSerialized = createSerializedExampleItemData()

        assertJSONObjectEquals(expectedSerialized, exampleItemData.serialize())
    }

    @Test
    fun `Deserialize an ItemData`() {
        val serializedItemData = createSerializedExampleItemData()
        val expectedItemData = createExampleItemData()

        assertEquals(expectedItemData, ItemData.Deserializer.deserializeOrNull(serializedItemData))
    }

    @Test
    fun `Deserialize an invalid ItemData returns null`() {
        val serializedItemData = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedItemData = null

        assertEquals(expectedItemData, ItemData.Deserializer.deserializeOrNull(serializedItemData))
    }

    companion object {
        private fun createExampleItemData(): ItemData {
            return ItemData(
                title = "exampleTitle",
                password = "examplePassword"
            )
        }

        private fun createSerializedExampleItemData(): JSONObject {
            return JSONObject(
                """
                {
                  "title": "exampleTitle",
                  "password": "examplePassword"
                }
                """.trimIndent()
            )
        }
    }
}