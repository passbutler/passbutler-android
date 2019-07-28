package de.sicherheitskritisch.passbutler.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DerivationLocalAuthenticationHashTest {

    /**
     * Test values can be generated with following shell command:
     *
     * $ USERNAME="testuser";
     * $ PASSWORD="1234abcd";
     * $ SALT=$(echo -n "$USERNAME" | od -A n -t x1 | sed 's/ //g');
     * $ echo -n "$PASSWORD" | nettle-pbkdf2 -i 100001 -l 32 --hex-salt $SALT | sed 's/ //g' | tr a-z A-Z
     */

    @Test
    fun `Derive a local authentication hash from username and password`() {
        val username = "testuser"
        val password = "1234abcd"

        val derivedHash = Derivation.deriveLocalAuthenticationHash(username, password)
        assertEquals("E8DCDA8125DBBAF57893AD24490096C28C0C079762CB48CE045D770E8CF41D45", derivedHash)
    }

    @Test
    fun `Leading and trailing spaces in username does not matter for derive a authentication hash`() {
        val password = "1234abcd"

        val usernameWithSpaces = " testuser  "
        val derivedHashWithSpaces = Derivation.deriveLocalAuthenticationHash(usernameWithSpaces, password)

        val usernameWithoutSpaces = "testuser"
        val derivedHashWithoutSpaces = Derivation.deriveLocalAuthenticationHash(usernameWithoutSpaces, password)

        assertEquals(derivedHashWithSpaces, derivedHashWithoutSpaces)
    }

    @Test
    fun `Leading and trailing spaces in password does not matter for derive a authentication hash`() {
        val username = "testuser"

        val passwordWithSpaces = " 1234abcd  "
        val derivedHashWithSpaces = Derivation.deriveLocalAuthenticationHash(username, passwordWithSpaces)

        val passwordWithoutSpaces = "1234abcd"
        val derivedHashWithoutSpaces = Derivation.deriveLocalAuthenticationHash(username, passwordWithoutSpaces)

        assertEquals(derivedHashWithSpaces, derivedHashWithoutSpaces)
    }
}