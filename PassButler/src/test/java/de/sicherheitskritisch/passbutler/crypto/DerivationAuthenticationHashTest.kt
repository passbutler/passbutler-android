package de.sicherheitskritisch.passbutler.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DerivationAuthenticationHashTest {

    /**
     * Valid authentication hash derivation tests.
     *
     * Test values can be generated with following shell command:
     * $ SALT=$(echo -n "testuser" | od -A n -t x1 | sed 's/ //g'); echo -n "1234abcd" | nettle-pbkdf2 -i 100001 -l 32 --hex-salt $SALT | sed 's/ //g' | tr a-z A-Z
     */

    @Test
    fun `Derive an authentication hash from username and password`() {
        val username = "testuser"
        val password = "1234abcd"

        val derivedHash = Derivation.deriveAuthenticationHash(username, password)
        assertEquals("E8DCDA8125DBBAF57893AD24490096C28C0C079762CB48CE045D770E8CF41D45", derivedHash)
    }

    @Test
    fun `Leading and trailing spaces in username does not matter for derive a authentication hash`() {
        val password = "1234abcd"

        val usernameWithSpaces = " testuser  "
        val derivedHashWithSpaces = Derivation.deriveAuthenticationHash(usernameWithSpaces, password)

        val usernameWithoutSpaces = "testuser"
        val derivedHashWithoutSpaces = Derivation.deriveAuthenticationHash(usernameWithoutSpaces, password)

        assertEquals(derivedHashWithSpaces, derivedHashWithoutSpaces)
    }

    @Test
    fun `Leading and trailing spaces in password does not matter for derive a authentication hash`() {
        val username = "testuser"

        val passwordWithSpaces = " 1234abcd  "
        val derivedHashWithSpaces = Derivation.deriveAuthenticationHash(username, passwordWithSpaces)

        val passwordWithoutSpaces = "1234abcd"
        val derivedHashWithoutSpaces = Derivation.deriveAuthenticationHash(username, passwordWithoutSpaces)

        assertEquals(derivedHashWithSpaces, derivedHashWithoutSpaces)
    }
}