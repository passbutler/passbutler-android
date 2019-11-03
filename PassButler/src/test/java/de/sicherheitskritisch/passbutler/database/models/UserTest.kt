package de.sicherheitskritisch.passbutler.database.models

import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class UserTest {

    @Test
    fun `Serialize and deserialize a User should result an equal object`() {
        mockkObject(ProtectedValue.Companion)

        val modifiedDate: Long = 12345678
        val createdDate: Long = 12345679

        val exampleUser = User(
            "myUserName",
            createTestMasterPasswordAuthenticationHash(),
            createTestKeyDerivationInformation(),
            createTestProtectedValueMasterEncryptionKey(),
            createTestItemEncryptionPublicKey(),
            createTestItemEncryptionSecretKey(),
            createTestProtectedValueSettings(),
            true,
            Date(modifiedDate),
            Date(createdDate)
        )

        val serializedUser = exampleUser.serialize()
        val deserializedUser = User.DefaultUserDeserializer.deserializeOrNull(serializedUser)

        assertEquals(exampleUser, deserializedUser)
    }

    companion object {
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