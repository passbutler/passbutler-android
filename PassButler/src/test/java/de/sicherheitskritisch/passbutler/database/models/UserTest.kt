package de.sicherheitskritisch.passbutler.database.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.hexToBytes
import de.passbutler.common.toDate
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserTest {

    @Test
    fun `Serialize and deserialize a User should result an equal object`() {
        val exampleUser = createExampleUser()

        val serializedUser = exampleUser.serialize()
        val deserializedUser = User.DefaultUserDeserializer.deserializeOrNull(serializedUser)

        assertEquals(exampleUser, deserializedUser)
    }

    @Test
    fun `Serialize an User`() {
        val exampleUser = createExampleUser()
        val expectedSerialized = createSerializedExampleUser()

        assertJSONObjectEquals(expectedSerialized, exampleUser.serialize())
    }

    @Test
    fun `Deserialize an User`() {
        val serializedUser = createSerializedExampleUser()
        val expectedUser = createExampleUser()

        assertEquals(expectedUser, User.DefaultUserDeserializer.deserializeOrNull(serializedUser))
    }

    @Test
    fun `Deserialize an invalid User returns null`() {
        val serializedUser = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedUser = null

        assertEquals(expectedUser, User.DefaultUserDeserializer.deserializeOrNull(serializedUser))
    }

    companion object {
        private fun createExampleUser(): User {

            return User(
                username = "myUserName",
                masterPasswordAuthenticationHash = createTestMasterPasswordAuthenticationHash(),
                masterKeyDerivationInformation = createTestKeyDerivationInformation(),
                masterEncryptionKey = createTestProtectedValueMasterEncryptionKey(),
                itemEncryptionPublicKey = createTestItemEncryptionPublicKey(),
                itemEncryptionSecretKey = createTestItemEncryptionSecretKey(),
                settings = createTestProtectedValueSettings(),
                deleted = true,
                modified = "2019-12-27T12:00:01+0000".toDate(),
                created = "2019-12-27T12:00:00+0000".toDate()
            )
        }

        private fun createSerializedExampleUser(): JSONObject {
            return JSONObject(
                """
                {
                  "username": "myUserName",
                  "masterPasswordAuthenticationHash": "pbkdf2:sha256:150000${'$'}nww6C11M${'$'}241ac264e71f35826b8a475bdeb8c6b231a4de2b228f7af979f246c24b4905de",
                  "masterKeyDerivationInformation": {
                    "iterationCount": 1234,
                    "salt": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
                  },
                  "masterEncryptionKey": {
                    "initializationVector": "qqqqqqqqqqqqqqqq",
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM"
                  },
                  "itemEncryptionPublicKey": {
                    "key": "qrvM"
                  },
                  "itemEncryptionSecretKey": {
                    "initializationVector": "zMzMzMzMzMzMzMzM",
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM"
                  },
                  "settings": {
                    "initializationVector": "u7u7u7u7u7u7u7u7",
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM"
                  },
                  "deleted": true,
                  "modified": 1577448001000,
                  "created": 1577448000000
                }
                """.trimIndent()
            )
        }

        private fun createTestMasterPasswordAuthenticationHash(): String {
            return "pbkdf2:sha256:150000\$nww6C11M\$241ac264e71f35826b8a475bdeb8c6b231a4de2b228f7af979f246c24b4905de"
        }

        private fun createTestKeyDerivationInformation(): KeyDerivationInformation {
            val salt = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
            val iterationCount = 1234
            val testMasterKeyDerivationInformation = KeyDerivationInformation(salt, iterationCount)
            return testMasterKeyDerivationInformation
        }

        private fun createTestProtectedValueMasterEncryptionKey(): ProtectedValue<CryptographicKey> {
            val testProtectedValueMasterEncryptionKey = ProtectedValue.createInstanceForTesting<CryptographicKey>(
                "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )
            return testProtectedValueMasterEncryptionKey
        }

        private fun createTestItemEncryptionPublicKey() = CryptographicKey("AABBCC".hexToBytes())

        private fun createTestItemEncryptionSecretKey(): ProtectedValue<CryptographicKey> {
            val testProtectedValueMasterEncryptionKey = ProtectedValue.createInstanceForTesting<CryptographicKey>(
                "CCCCCCCCCCCCCCCCCCCCCCCC".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )
            return testProtectedValueMasterEncryptionKey
        }

        private fun createTestProtectedValueSettings(): ProtectedValue<UserSettings> {
            val testProtectedValueSettings = ProtectedValue.createInstanceForTesting<UserSettings>(
                "BBBBBBBBBBBBBBBBBBBBBBBB".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )
            return testProtectedValueSettings
        }
    }
}
