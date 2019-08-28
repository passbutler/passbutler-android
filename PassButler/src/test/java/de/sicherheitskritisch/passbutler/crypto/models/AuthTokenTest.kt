package de.sicherheitskritisch.passbutler.crypto.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.crypto.models.AuthToken
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthTokenTest {

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