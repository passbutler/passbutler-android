package de.sicherheitskritisch.passbutler.models

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

internal class UserTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun unsetUp() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `Serialize and deserialize a User should result an equal object`() {
        val modifiedDate: Long = 12345678
        val createdDate: Long = 12345679

        val exampleUser = User(
            username = "myUserName",
            lockTimeout = 1337,
            deleted = true,
            modified = Date(modifiedDate),
            created = Date(createdDate)
        )

        val serializedUser = exampleUser.serialize()
        val deserializedUser = User.deserialize(serializedUser)

        assertEquals(exampleUser, deserializedUser)
    }
}