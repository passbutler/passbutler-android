package de.passbutler.common.crypto

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class RandomGeneratorTest {

    @BeforeEach
    fun setUp() {
        mockkObject(RandomGenerator)

        val mockRandomInstance = mockk<SecureRandom>()
        every { mockRandomInstance.nextInt(ALLOWED_CHARACTERS.length) } returns 2

        // Return a mocked `Random` instance that returns static value for `nextInt` be sure tests can be reproduced
        every { RandomGenerator.createRandomInstance() } returns mockRandomInstance
    }

    @AfterEach
    fun unsetUp() {
        unmockkAll()
    }

    @Test
    fun `Generate random string`() {
        val randomString = runBlocking { RandomGenerator.generateRandomString(5, ALLOWED_CHARACTERS) }
        assertEquals("ccccc", randomString)
    }

    companion object {
        private const val ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}