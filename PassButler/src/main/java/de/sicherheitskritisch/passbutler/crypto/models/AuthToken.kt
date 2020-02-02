package de.sicherheitskritisch.passbutler.crypto.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONSerializableDeserializer
import de.sicherheitskritisch.passbutler.base.JSONWebToken
import de.sicherheitskritisch.passbutler.base.putString
import org.json.JSONException
import org.json.JSONObject
import org.tinylog.kotlin.Logger
import java.time.Instant

/**
 * Wraps a auth token string (actually a JSON Web Token) in a `JSONSerializable`.
 */
data class AuthToken(
    val token: String
) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_TOKEN, token)
        }
    }

    object Deserializer : JSONSerializableDeserializer<AuthToken>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): AuthToken {
            return AuthToken(
                token = jsonObject.getString(SERIALIZATION_KEY_TOKEN)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_TOKEN = "token"
    }
}

val AuthToken.expirationDate: Instant?
    get() {
        return try {
            JSONWebToken.getExpiration(token)
        } catch (exception: Exception) {
            Logger.warn("The expiration date of the JWT could not be determined")
            null
        }
    }

val AuthToken?.isExpired: Boolean
    get() = this?.expirationDate?.let { it < Instant.now() } ?: true