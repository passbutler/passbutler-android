package de.sicherheitskritisch.passbutler.database.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONWebToken
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.putString
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

data class AuthToken(
    val token: String
) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_TOKEN, token)
        }
    }

    companion object {
        fun deserialize(jsonObject: JSONObject): AuthToken? {
            return try {
                AuthToken(
                    token = jsonObject.getString(SERIALIZATION_KEY_TOKEN)
                )
            } catch (e: JSONException) {
                L.w("AuthToken", "The auth token could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }

        private const val SERIALIZATION_KEY_TOKEN = "token"
    }
}

val AuthToken.expirationDate: Date?
    get() {
        return try {
            JSONWebToken.getExpiration(token)
        } catch (e: Exception) {
            L.w("AuthToken", "The expirationDate date of the JWT could not be determined!")
            null
        }
    }
