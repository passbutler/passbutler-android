package de.passbutler.common.crypto.models

import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.JSONWebToken
import de.passbutler.common.base.putString
import org.json.JSONException
import org.json.JSONObject
import org.tinylog.kotlin.Logger
import java.time.Instant

/**
 * Wraps a authentication token string (actually a JSON Web Token) in a `JSONSerializable`.
 */
data class AuthToken(val token: String) : JSONSerializable {

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