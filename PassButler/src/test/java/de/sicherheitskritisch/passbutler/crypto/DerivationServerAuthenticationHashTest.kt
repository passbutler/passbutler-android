package de.sicherheitskritisch.passbutler.crypto

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DerivationServerAuthenticationHashTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(RandomGenerator::class)
        every { RandomGenerator.generateRandomString(SERVER_AUTHENTICATION_HASH_SALT_LENGTH, SERVER_AUTHENTICATION_HASH_SALT_VALID_CHARACTERS) } returns "abcdefgh"
    }

    @AfterEach
    fun unsetUp() {
        unmockkAll()
    }

    /**
     * Valid authentication hash derivation tests.
     *
     * Test values can be generated with following shell command:
     * TODO
     */

    @Test
    fun `Derive a server authentication hash`() {
        val password = "1234abcd"

        val derivedHash = Derivation.deriveServerAuthenticationHash(password)
        assertEquals("pbkdf2:sha256:150000\$abcdefgh\$e6b929bdae73863bff72d05be560a47c9b026a38233532fdd978ca315e5ea982", derivedHash)
    }
}