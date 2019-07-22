package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.hexToBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DerivationTest {

    /**
     * Invalid password tests
     */

    @Test
    fun `Try to derive a key with empty password throws an exception`() {
        val userPassword = ""
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            Derivation.deriveSymmetricKey(userPassword, keyDerivationInformation)
        }

        assertEquals("The password must not be empty!", exception.message)
    }

    @Test
    fun `Try to derive a key with only-whitespace-containing password throws an exception`() {
        val userPassword = "   "
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            Derivation.deriveSymmetricKey(userPassword, keyDerivationInformation)
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
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            Derivation.deriveSymmetricKey(userPassword, keyDerivationInformation)
        }

        assertEquals("The salt must be 256 bits long!", exception.message)
    }

    @Test
    fun `Try to derive a key with too short salt throws an exception`() {
        val userPassword = "1234abcd"
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            Derivation.deriveSymmetricKey(userPassword, keyDerivationInformation)
        }

        assertEquals("The salt must be 256 bits long!", exception.message)
    }

    @Test
    fun `Try to derive a key with too long salt throws an exception`() {
        val userPassword = "1234abcd"
        val salt = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes()
        val iterationCount = 1000
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            Derivation.deriveSymmetricKey(userPassword, keyDerivationInformation)
        }

        assertEquals("The salt must be 256 bits long!", exception.message)
    }

    /**
     * Valid key derivation tests.
     *
     * Test values generated with "nettle" command line program, e.g.:
     * $ echo -n "1234abcd" | nettle-pbkdf2 -i 100000 -l 32 --hex-salt 007A1D97CB4B60D69F323E67C25014845E9693A16352C4A032D677AF16F036C1
     */

    @Test
    fun `Derive a key from password and salt with 1000 iterations`() {
        val userPassword = "1234abcd"
        val salt = "B2BA57DCB27DD154C0699AB84A24D5D367C047F8C64FE52CFA078047AA0298B2".hexToBytes()
        val iterationCount = 1000
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val derivedKey = Derivation.deriveSymmetricKey(userPassword, keyDerivationInformation)
        assertArrayEquals("8A803738E7D84E90A607ABB9CCE4E6C10E14F4856B4B8F6D3A2DB0EFC48456EB".hexToBytes(), derivedKey)
    }

    @Test
    fun `Derive a key from password and salt with 100000 iterations`() {
        val userPassword = "1234abcd"
        val salt = "007A1D97CB4B60D69F323E67C25014845E9693A16352C4A032D677AF16F036C1".hexToBytes()
        val iterationCount = 100_000
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val derivedKey = Derivation.deriveSymmetricKey(userPassword, keyDerivationInformation)
        assertArrayEquals("10869F0AB3966CA9EF91660167EA6416C30CCE8A1F6C4A7DAB0E465E6D608598".hexToBytes(), derivedKey)
    }

    /**
     * Password preparation tests
     */

    @Test
    fun `Derive a key from password with leading and trailing spaces results in same key as password without`() {
        val salt = "B2BA57DCB27DD154C0699AB84A24D5D367C047F8C64FE52CFA078047AA0298B2".hexToBytes()
        val iterationCount = 1000
        val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val userPasswordWithSpaces = "  1234abcd  "
        val derivedKeyWithSpaces = Derivation.deriveSymmetricKey(userPasswordWithSpaces, keyDerivationInformation)

        val userPasswordWithoutSpaces = "1234abcd"
        val derivedKeyWithoutSpaces = Derivation.deriveSymmetricKey(userPasswordWithoutSpaces, keyDerivationInformation)

        assertArrayEquals(derivedKeyWithSpaces, derivedKeyWithoutSpaces)
    }
}