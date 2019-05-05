package de.sicherheitskritisch.passbutler.database.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.putBoolean
import de.sicherheitskritisch.passbutler.base.putInt
import org.json.JSONException
import org.json.JSONObject

// TODO: Add unit tests for serialization/deserialization
data class UserSettings(
    val lockTimeout: Int = DEFAULT_LOCK_TIMEOUT,
    val hidePasswords: Boolean = DEFAULT_HIDE_PASSWORDS
) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putInt(SERIALIZATION_KEY_LOCK_TIMEOUT, lockTimeout)
            putBoolean(SERIALIZATION_KEY_HIDE_PASSWORDS, hidePasswords)
        }
    }

    companion object {
        private const val DEFAULT_LOCK_TIMEOUT = 2
        private const val DEFAULT_HIDE_PASSWORDS = true

        private const val SERIALIZATION_KEY_LOCK_TIMEOUT = "lockTimeoutSetting"
        private const val SERIALIZATION_KEY_HIDE_PASSWORDS = "hidePasswords"

        fun deserialize(jsonObject: JSONObject): UserSettings? {
            return try {
                UserSettings(
                    jsonObject.getInt(SERIALIZATION_KEY_LOCK_TIMEOUT),
                    jsonObject.getBoolean(SERIALIZATION_KEY_HIDE_PASSWORDS)
                )
            } catch (e: JSONException) {
                L.w("UserSettings", "The UserSettings could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }
    }
}