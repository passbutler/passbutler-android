package de.passbutler.common.base

import org.json.JSONException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class JSONWebTokenTest {
    @Test
    fun `Valid JWT with expiration date contained`() {
        val jwt =
            "eyJhbGciOiJIUzUxMiIsImV4cCI6MTU2NDA4Njc0NiwiaWF0IjoxNTY0MDgzMTQ2fQ.eyJ1c2VybmFtZSI6InRlc3R1c2VyIn0.lh87lqAPe59z2pMUD8lRvpMduDv4dfhKjageg6MI7_nREcsE1VQ0Z0L_kVO_FdpsvVfzhPBZG5dSWdp83y9FRg"
        val expirationDate = JSONWebToken.getExpiration(jwt)

        assertEquals(Instant.parse("2019-07-25T20:32:26Z"), expirationDate)
    }

    @Test
    fun `Valid JWT without expiration date contained`() {
        val jwt = "eyJhbGciOiJIUzUxMiIsImlhdCI6MTU2NDA4MzE0Nn0.eyJ1c2VybmFtZSI6InRlc3R1c2VyIn0.s4Tyi3uaFrYchDH7U8HrMyPjuTYuOa4uuAGrowO8PnC01BTYCt5OdRN45-keNx8D1mOOqLAscK7vY9lRdPo7HQ"

        val exception = assertThrows(JSONException::class.java) {
            JSONWebToken.getExpiration(jwt)
        }

        assertEquals("JSONObject[\"exp\"] not found.", exception.message)
    }

    @Test
    fun `Invalid JWT`() {
        val jwt = "foobar"

        val exception = assertThrows(IllegalArgumentException::class.java) {
            JSONWebToken.getExpiration(jwt)
        }

        assertEquals("Invalid JSON Web Token!", exception.message)
    }
}