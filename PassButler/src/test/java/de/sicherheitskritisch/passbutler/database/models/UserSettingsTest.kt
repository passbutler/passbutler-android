package de.sicherheitskritisch.passbutler.database.models

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class UserSettingsTest {

    @Test
    fun `Serialize and deserialize a UserSettings should result an equal object`() {
        val exampleUserSettings = UserSettings(
            1234,
            true
        )

        val serializedUserSettings = exampleUserSettings.serialize()
        val deserializedUserSettings = UserSettings.Deserializer.deserializeOrNull(serializedUserSettings)

        Assertions.assertEquals(exampleUserSettings, deserializedUserSettings)
    }
}