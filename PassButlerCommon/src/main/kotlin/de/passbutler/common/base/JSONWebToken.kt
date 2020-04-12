package de.passbutler.common.base

import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.util.*

object JSONWebToken {
    @Throws(IllegalArgumentException::class, JSONException::class)
    fun getExpiration(jwt: String): Instant {
        val splittedJWT = jwt.split(".")
        require(splittedJWT.size == 3) { "Invalid JSON Web Token!" }

        val encodedHeader = splittedJWT[0]
        val decodedHeader = Base64.getUrlDecoder().decode(encodedHeader).toUTF8String()
        val jwtHeader = JSONObject(decodedHeader)

        return Instant.ofEpochSecond(jwtHeader.getLong("exp"))
    }
}