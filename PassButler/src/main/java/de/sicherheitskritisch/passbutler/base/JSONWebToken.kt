package de.sicherheitskritisch.passbutler.base

import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import java.util.*

object JSONWebToken {
    @Throws(IllegalArgumentException::class, JSONException::class)
    fun getExpiration(jwt: String): Date {
        val splittedJWT = jwt.split(".")

        if (splittedJWT.size != 3) {
            throw IllegalArgumentException("Invalid JSON Web Token!")
        }

        val encodedPayload = splittedJWT[2]
        val decodedPayload = Base64.decode(encodedPayload, Base64.URL_SAFE).toString(Charsets.UTF_8)
        val jwtPayload = JSONObject(decodedPayload)

        return Date(jwtPayload.getLong("exp"))
    }
}