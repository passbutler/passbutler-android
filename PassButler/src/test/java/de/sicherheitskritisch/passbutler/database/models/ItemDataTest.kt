package de.sicherheitskritisch.passbutler.database.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ItemDataTest {

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