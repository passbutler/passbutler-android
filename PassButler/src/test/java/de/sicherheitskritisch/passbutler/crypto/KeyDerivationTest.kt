package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.hexToBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class KeyDerivationTest {

    /**
     * Invalid password tests
     */

    @Test
    fun `Try to derive a key with empty password throws an exception`() {
        val userPassword = ""
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000

        val exception = assertThrows(IllegalArgumentException::class.java) {
            KeyDerivation.deriveAES256KeyFromPassword(userPassword, salt, iterationCount)
        }

        assertEquals("The password must not be empty!", exception.message)
    }

    @Test
    fun `Try to derive a key with only-whitespace-containing password throws an exception`() {
        val userPassword = "   "
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000

        val exception = assertThrows(IllegalArgumentException::class.java) {
            KeyDerivation.deriveAES256KeyFromPassword(userPassword, salt, iterationCount)
        }

        assertEquals("The password must not be empty!", exception.message)
    }

    /**
     * Invalid salt tests
     */

    @Test
    fun `Try to derive a key with empty salt throws an exception`() {
        val userPassword = "1234abcd"
        val salt = "".hexToBytes()
        val iterationCount = 1000

        val exception = assertThrows(IllegalArgumentException::class.java) {
            KeyDerivation.deriveAES256KeyFromPassword(userPassword, salt, iterationCount)
        }

        assertEquals("The salt must be 256 bits long!", exception.message)
    }

    @Test
    fun `Try to derive a key with too short salt throws an exception`() {
        val userPassword = "1234abcd"
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000

        val exception = assertThrows(IllegalArgumentException::class.java) {
            KeyDerivation.deriveAES256KeyFromPassword(userPassword, salt, iterationCount)
        }

        assertEquals("The salt must be 256 bits long!", exception.message)
    }

    @Test
    fun `Try to derive a key with too long salt throws an exception`() {
        val userPassword = "1234abcd"
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000

        val exception = assertThrows(IllegalArgumentException::class.java) {
            KeyDerivation.deriveAES256KeyFromPassword(userPassword, salt, iterationCount)
        }

        assertEquals("The salt must be 256 bits long!", exception.message)
    }

    /**
     * Valid key derivation tests (test values generated with: <https://asecuritysite.com/encryption/PBKDF2z>)
     */

    @Test
    fun `Derive a key from password and salt with 1000 iterations`() {
        val userPassword = "1234abcd"
        val salt = "B2BA57DCB27DD154C0699AB84A24D5D367C047F8C64FE52CFA078047AA0298B2".hexToBytes()
        val iterationCount = 1000

        val derivedKey = KeyDerivation.deriveAES256KeyFromPassword(userPassword, salt, iterationCount)
        assertArrayEquals("8F2984F7BF32EF34E64EEA14C8865899CE0783D39D0DF9B82ADB8D3097A02A10".hexToBytes(), derivedKey)
    }

    @Test
    fun `Derive a key from password and salt with 100000 iterations`() {
        val userPassword = "1234abcd"
        val salt = "007A1D97CB4B60D69F323E67C25014845E9693A16352C4A032D677AF16F036C1".hexToBytes()
        val iterationCount = 100_000

        val derivedKey = KeyDerivation.deriveAES256KeyFromPassword(userPassword, salt, iterationCount)
        assertArrayEquals("493242BAD12E66F6E1A2CE3BDF7390693A4552FDDCC6EECA94B0A2E6C4C40D5B".hexToBytes(), derivedKey)
    }

    /**
     * Password preparation tests
     */

    @Test
    fun `Derive a key from password with leading and trailing spaces results in same key as password without`() {
        val salt = "B2BA57DCB27DD154C0699AB84A24D5D367C047F8C64FE52CFA078047AA0298B2".hexToBytes()
        val iterationCount = 1000

        val userPasswordWithSpaces = "  1234abcd  "
        val derivedKeyWithSpaces = KeyDerivation.deriveAES256KeyFromPassword(userPasswordWithSpaces, salt, iterationCount)

        val userPasswordWithoutSpaces = "1234abcd"
        val derivedKeyWithoutSpaces = KeyDerivation.deriveAES256KeyFromPassword(userPasswordWithoutSpaces, salt, iterationCount)

        assertArrayEquals(derivedKeyWithSpaces, derivedKeyWithoutSpaces)
    }
}