package de.passbutler.app.database.models

import de.passbutler.common.assertJSONObjectEquals
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserSettingsTest {

    @Test
    fun `Serialize and deserialize a UserSettings should result an equal object`() {
        val exampleUserSettings = createExampleUserSettings()

        val serializedUserSettings = exampleUserSettings.serialize()
        val deserializedUserSettings = UserSettings.Deserializer.deserializeOrNull(serializedUserSettings)

        assertEquals(exampleUserSettings, deserializedUserSettings)
    }

    @Test
    fun `Serialize an UserSettings`() {
        val exampleUserSettings = createExampleUserSettings()
        val expectedSerialized = createSerializedExampleUserSettings()

        assertJSONObjectEquals(expectedSerialized, exampleUserSettings.serialize())
    }

    @Test
    fun `Deserialize an UserSettings`() {
        val serializedUserSettings = createSerializedExampleUserSettings()
        val expectedUserSettings = createExampleUserSettings()

        assertEquals(expectedUserSettings, UserSettings.Deserializer.deserializeOrNull(serializedUserSettings))
    }

    @Test
    fun `Deserialize an invalid UserSettings returns null`() {
        val serializedUserSettings = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedUserSettings = null

        assertEquals(expectedUserSettings, UserSettings.Deserializer.deserializeOrNull(serializedUserSettings))
    }

    companion object {
        private fun createExampleUserSettings(): UserSettings {
            return UserSettings(
                automaticLockTimeout = 1234,
                hidePasswords = true
            )
        }

        private fun createSerializedExampleUserSettings(): JSONObject {
            return JSONObject(
                """
                {
                  "automaticLockTimeout": 1234,
                  "hidePasswords": true
                }
                """.trimIndent()
            )
        }
    }
}