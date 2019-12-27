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

class UserSettingsTest {

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