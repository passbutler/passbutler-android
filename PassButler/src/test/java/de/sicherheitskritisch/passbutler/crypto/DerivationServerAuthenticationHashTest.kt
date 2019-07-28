package de.sicherheitskritisch.passbutler.crypto

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DerivationServerAuthenticationHashTest {

    @BeforeEach
    fun setUp() {
        mockkObject(RandomGenerator)

        // Return static salt to be sure tests can be reproduced
        every { RandomGenerator.generateRandomString(SERVER_AUTHENTICATION_HASH_SALT_LENGTH, SERVER_AUTHENTICATION_HASH_SALT_VALID_CHARACTERS) } returns STATIC_SALT
    }

    @AfterEach
    fun unsetUp() {
        unmockkAll()
    }

    /**
     * Test values can be generated with following Python code:
     *
     * from werkzeug.security import _hash_internal
     * salt = 'abcdefgh'
     * password = '1234abcd'
     * hash = _hash_internal(method='pbkdf2:sha256:150000', salt=salt, password=password)
     * print("{}${}${}".format(hash[1], salt, hash[0]))
     */

    @Test
    fun `Derive a server authentication hash`() {
        val password = "1234abcd"

        val derivedHash = Derivation.deriveServerAuthenticationHash(password)
        assertEquals("pbkdf2:sha256:150000\$abcdefgh\$e6b929bdae73863bff72d05be560a47c9b026a38233532fdd978ca315e5ea982", derivedHash)
    }

    companion object {
        private const val STATIC_SALT = "abcdefgh"
    }
}