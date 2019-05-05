package de.sicherheitskritisch.passbutler.database.models

import android.util.Log
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class UserTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun unsetUp() {
        unmockkAll()
    }

    @Test
    fun `Serialize and deserialize a User should result an equal object`() {
        mockkObject(ProtectedValue.Companion)

        val salt = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val iterationCount = 1234
        val testMasterKeyDerivationInformation = KeyDerivationInformation(salt, iterationCount)

        val testProtectedValueMasterEncryptionKey = ProtectedValue<CryptographicKey>(
            "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
            EncryptionAlgorithm.AES256GCM,
            "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        )

        val testProtectedValueSettings = ProtectedValue<UserSettings>(
            "BBBBBBBBBBBBBBBBBBBBBBBB".hexToBytes(),
            EncryptionAlgorithm.AES256GCM,
            "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        )

        val modifiedDate: Long = 12345678
        val createdDate: Long = 12345679

        val exampleUser = User(
            "myUserName",
            testMasterKeyDerivationInformation,
            testProtectedValueMasterEncryptionKey,
            testProtectedValueSettings,
            true,
            Date(modifiedDate),
            Date(createdDate)
        )

        val serializedUser = exampleUser.serialize()
        val deserializedUser = User.deserialize(serializedUser)

        assertEquals(exampleUser, deserializedUser)
    }
}