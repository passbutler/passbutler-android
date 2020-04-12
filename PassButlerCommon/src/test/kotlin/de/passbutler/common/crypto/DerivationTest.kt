package de.passbutler.common.crypto

import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.hexToBytes
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DerivationTest {

    @Nested
    inner class MasterKeyDerivation {

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
                runBlocking { Derivation.deriveMasterKey(userPassword, keyDerivationInformation) }
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
                runBlocking { Derivation.deriveMasterKey(userPassword, keyDerivationInformation) }
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
                runBlocking { Derivation.deriveMasterKey(userPassword, keyDerivationInformation) }
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
                runBlocking { Derivation.deriveMasterKey(userPassword, keyDerivationInformation) }
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
                runBlocking { Derivation.deriveMasterKey(userPassword, keyDerivationInformation) }
            }
            assertEquals("The salt must be 256 bits long!", exception.message)
        }

        /**
         * Valid key derivation tests.
         *
         * Test values can be generated with following shell command:
         * $ echo -n "1234abcd" | nettle-pbkdf2 -i 100000 -l 32 --hex-salt 007A1D97CB4B60D69F323E67C25014845E9693A16352C4A032D677AF16F036C1 | sed 's/ //g' | tr a-z A-Z
         */

        @Test
        fun `Derive a key from password and salt with 1000 iterations`() {
            val userPassword = "1234abcd"
            val salt = "B2BA57DCB27DD154C0699AB84A24D5D367C047F8C64FE52CFA078047AA0298B2".hexToBytes()
            val iterationCount = 1000
            val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

            val derivedKey = runBlocking { Derivation.deriveMasterKey(userPassword, keyDerivationInformation).resultOrThrowException() }
            assertArrayEquals("8A803738E7D84E90A607ABB9CCE4E6C10E14F4856B4B8F6D3A2DB0EFC48456EB".hexToBytes(), derivedKey)
        }

        @Test
        fun `Derive a key from password and salt with 100000 iterations`() {
            val userPassword = "1234abcd"
            val salt = "007A1D97CB4B60D69F323E67C25014845E9693A16352C4A032D677AF16F036C1".hexToBytes()
            val iterationCount = 100_000
            val keyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

            val derivedKey = runBlocking { Derivation.deriveMasterKey(userPassword, keyDerivationInformation).resultOrThrowException() }
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
            val derivedKeyWithSpaces = runBlocking { Derivation.deriveMasterKey(userPasswordWithSpaces, keyDerivationInformation).resultOrThrowException() }

            val userPasswordWithoutSpaces = "1234abcd"
            val derivedKeyWithoutSpaces = runBlocking { Derivation.deriveMasterKey(userPasswordWithoutSpaces, keyDerivationInformation).resultOrThrowException() }

            assertArrayEquals(derivedKeyWithSpaces, derivedKeyWithoutSpaces)
        }
    }

    @Nested
    inner class LocalAuthenticationHashDerivation {

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

            val derivedHash = runBlocking { Derivation.deriveLocalAuthenticationHash(username, password).resultOrThrowException() }

            assertEquals("e8dcda8125dbbaf57893ad24490096c28c0c079762cb48ce045d770e8cf41d45", derivedHash)
        }

        @Test
        fun `Leading and trailing spaces in username does not matter for derive a authentication hash`() {
            val password = "1234abcd"

            val usernameWithSpaces = " testuser  "
            val derivedHashWithSpaces = runBlocking { Derivation.deriveLocalAuthenticationHash(usernameWithSpaces, password).resultOrThrowException() }

            val usernameWithoutSpaces = "testuser"
            val derivedHashWithoutSpaces = runBlocking { Derivation.deriveLocalAuthenticationHash(usernameWithoutSpaces, password).resultOrThrowException() }

            assertEquals(derivedHashWithSpaces, derivedHashWithoutSpaces)
        }

        @Test
        fun `Leading and trailing spaces in password does not matter for derive a authentication hash`() {
            val username = "testuser"

            val passwordWithSpaces = " 1234abcd  "
            val derivedHashWithSpaces = runBlocking { Derivation.deriveLocalAuthenticationHash(username, passwordWithSpaces).resultOrThrowException() }

            val passwordWithoutSpaces = "1234abcd"
            val derivedHashWithoutSpaces = runBlocking { Derivation.deriveLocalAuthenticationHash(username, passwordWithoutSpaces).resultOrThrowException() }

            assertEquals(derivedHashWithSpaces, derivedHashWithoutSpaces)
        }
    }

    @Nested
    inner class ServerAuthenticationHashDerivation {

        @BeforeEach
        fun setUp() {
            mockkObject(RandomGenerator)

            // Return static salt to be sure tests can be reproduced
            val staticSalt = "abcdefgh"
            coEvery { RandomGenerator.generateRandomString(
                SERVER_AUTHENTICATION_HASH_SALT_LENGTH,
                SERVER_AUTHENTICATION_HASH_SALT_VALID_CHARACTERS
            ) } returns staticSalt
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
            val derivedHash = runBlocking { Derivation.deriveServerAuthenticationHash(password).resultOrThrowException() }

            assertEquals("pbkdf2:sha256:150000\$abcdefgh\$e6b929bdae73863bff72d05be560a47c9b026a38233532fdd978ca315e5ea982", derivedHash)
        }
    }
}