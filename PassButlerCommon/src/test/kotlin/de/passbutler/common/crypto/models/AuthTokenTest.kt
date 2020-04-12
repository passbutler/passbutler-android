package de.passbutler.common.crypto.models

import de.passbutler.common.assertJSONObjectEquals
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthTokenTest {

    @Test
    fun `Serialize an AuthToken`() {
        val authToken = AuthToken("exampleToken")
        val expectedSerialized = JSONObject(
            """{"token":"exampleToken"}"""
        )

        assertJSONObjectEquals(expectedSerialized, authToken.serialize())
    }

    @Test
    fun `Deserialize an AuthToken`() {
        val serializedAuthToken = JSONObject(
            """{"token":"exampleToken"}"""
        )
        val expectedAuthToken = AuthToken("exampleToken")

        assertEquals(expectedAuthToken, AuthToken.Deserializer.deserializeOrNull(serializedAuthToken))
    }

    @Test
    fun `Deserialize an invalid AuthToken returns null`() {
        val serializedAuthToken = JSONObject(
            """{"foo":"exampleToken"}"""
        )
        val expectedAuthToken = null

        assertEquals(expectedAuthToken, AuthToken.Deserializer.deserializeOrNull(serializedAuthToken))
    }
}