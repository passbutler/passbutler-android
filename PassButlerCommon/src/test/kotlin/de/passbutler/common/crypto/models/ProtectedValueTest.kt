package de.passbutler.common.crypto.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.base.Failure
import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.Success
import de.passbutler.common.base.clear
import de.passbutler.common.base.putString
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.ProtectedValue.Companion.createInstanceForTesting
import de.passbutler.common.hexToBytes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtectedValueTest {

    /**
     * Serialization and deserialization tests
     */

    @Test
    fun `Serialize a protected value with short encrypted value and deserialize it than`() {
        val protectedValueReference = createSimpleTestProtectedValue(
            initializationVector = "1310aadeaa489ae84125c36a".hexToBytes(),
            encryptedValue = "4e692fce708b1b759cf61beb5c4e55a3a22d749c5839b3654d6cbe2299b3c28a".hexToBytes()
        )

        val expectedSerializedProtectedValue = JSONObject(
            """{"initializationVector": "ExCq3qpImuhBJcNq", "encryptionAlgorithm": "AES-256-GCM", "encryptedValue": "TmkvznCLG3Wc9hvrXE5Vo6ItdJxYObNlTWy+Ipmzwoo="}"""
        )

        val serializedProtectedValue = protectedValueReference.serialize()
        assertJSONObjectEquals(expectedSerializedProtectedValue, serializedProtectedValue)

        val deserializedProtectedValue = ProtectedValue.Deserializer<JSONSerializable>().deserializeOrNull(serializedProtectedValue)
        assertEquals(protectedValueReference, deserializedProtectedValue)
    }

    @Test
    fun `Serialize a protected value with longer encrypted value and deserialize it than`() {
        val protectedValueReference = createSimpleTestProtectedValue(
            initializationVector = "b263e025c3d0e60765e7eeba".hexToBytes(),
            encryptedValue = "0664c21c4485b0a37ebd3d0c5cba77c88ed4be3d8035b40390d8c32c6eaaa12dfd3d6fc19fa6b0d12092e9384f26e60747019c0294de426574b8a3d1dab2f5802a4db735952300b5da".hexToBytes()
        )

        val expectedSerializedProtectedValue = JSONObject(
            """{"initializationVector": "smPgJcPQ5gdl5+66", "encryptionAlgorithm": "AES-256-GCM", "encryptedValue": "BmTCHESFsKN+vT0MXLp3yI7Uvj2ANbQDkNjDLG6qoS39PW/Bn6aw0SCS6ThPJuYHRwGcApTeQmV0uKPR2rL1gCpNtzWVIwC12g=="}"""
        )

        val serializedProtectedValue = protectedValueReference.serialize()
        assertJSONObjectEquals(expectedSerializedProtectedValue, serializedProtectedValue)

        val deserializedProtectedValue = ProtectedValue.Deserializer<JSONSerializable>().deserializeOrNull(serializedProtectedValue)
        assertEquals(protectedValueReference, deserializedProtectedValue)
    }

    @Test
    fun `Deserialize a protected value returns null if the deserialization failed`() {
        val invalidSerializedProtectedValue = JSONObject()

        val deserializedProtectedValue = ProtectedValue.Deserializer<JSONSerializable>().deserializeOrNull(invalidSerializedProtectedValue)
        assertEquals(null, deserializedProtectedValue)
    }

    /**
     * Create tests
     */

    @Test
    fun `Create a protected value throws an exception if the encryption failed`() {
        val initializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(initializationVector, true)

        val unusedEncryptionKey = createNonClearedEncryptionKey()
        val testJSONSerializable = TestJSONSerializable("testValue")

        val result = runBlocking { ProtectedValue.create(mockAES256GCMAlgorithm, unusedEncryptionKey, testJSONSerializable) }
        val exception = (result as Failure).throwable

        assertTrue(exception is EncryptionFailedException)
    }

    @Test
    fun `Create a protected value and expect the given initial values`() {
        val initializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(initializationVector)

        val unusedEncryptionKey = createNonClearedEncryptionKey()
        val testJSONSerializable = TestJSONSerializable("testValue")

        val protectedValue = runBlocking { ProtectedValue.create(mockAES256GCMAlgorithm, unusedEncryptionKey, testJSONSerializable).resultOrThrowException() }

        assertArrayEquals(initializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.encryptionAlgorithm)

        // Create with `testJSONSerializable.serialize().toString().toByteArray(Charsets.UTF_8)`
        assertArrayEquals(byteArrayOf(123, 34, 116, 101, 115, 116, 70, 105, 101, 108, 100, 34, 58, 34, 116, 101, 115, 116, 86, 97, 108, 117, 101, 34, 125), protectedValue.encryptedValue)
    }

    @Test
    fun `Create a protected value with a cleared key throws an exception`() {
        val unusedInitializationVector = ByteArray(0)
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(unusedInitializationVector)

        val encryptionKey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes().also {
            // Clear key to make it invalid
            it.clear()
        }

        val testJSONSerializable = TestJSONSerializable("testValue")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ProtectedValue.create(mockAES256GCMAlgorithm, encryptionKey, testJSONSerializable) }
        }
        assertEquals("The given encryption key can't be used because it is cleared!", exception.message)
    }

    /**
     * Update tests
     */

    @Test
    fun `Update a protected value and expect an updated initialization vector and updated encrypted value`() {
        val updatedInitializationVector = "bbbbbbbbbbbbbbbbbbbbbbbb".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(updatedInitializationVector)

        val initialInitializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val initialEncryptedValue = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val protectedValue = createSimpleTestProtectedValue(
            initializationVector = initialInitializationVector,
            encryptedValue = initialEncryptedValue,
            encryptionAlgorithm = mockAES256GCMAlgorithm
        )

        val unusedEncryptionKey = createNonClearedEncryptionKey()
        val updatedJSONSerializable = TestJSONSerializable("testValue")

        runBlocking {
            protectedValue.update(unusedEncryptionKey, updatedJSONSerializable).resultOrThrowException()
        }

        assertArrayEquals(updatedInitializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.encryptionAlgorithm)
        assertArrayEquals(byteArrayOf(123, 34, 116, 101, 115, 116, 70, 105, 101, 108, 100, 34, 58, 34, 116, 101, 115, 116, 86, 97, 108, 117, 101, 34, 125), protectedValue.encryptedValue)
    }

    @Test
    fun `If it fails to update a protected value an exception is thrown and the initialization vector and updated encrypted value are not changed`() {
        val updatedInitializationVector = "bbbbbbbbbbbbbbbbbbbbbbbb".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(updatedInitializationVector, true)

        val initialInitializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val initialEncryptedValue = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val protectedValue = createSimpleTestProtectedValue(
            initializationVector = initialInitializationVector,
            encryptedValue = initialEncryptedValue,
            encryptionAlgorithm = mockAES256GCMAlgorithm
        )

        val unusedEncryptionKey = createNonClearedEncryptionKey()
        val updatedJSONSerializable = TestJSONSerializable("testValue")

        val result = runBlocking { protectedValue.update(unusedEncryptionKey, updatedJSONSerializable) }
        val exception = (result as Failure).throwable

        assertTrue(exception is EncryptionFailedException)

        assertArrayEquals(initialInitializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.encryptionAlgorithm)
        assertArrayEquals(initialEncryptedValue, protectedValue.encryptedValue)
    }

    @Test
    fun `Update a protected value with a cleared key throws an exception`() {
        val unusedInitialInitializationVector = ByteArray(0)
        val unusedInitialEncryptedValue = ByteArray(0)

        val unusedUpdatedInitializationVector = ByteArray(0)
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(unusedUpdatedInitializationVector)

        val protectedValue = createSimpleTestProtectedValue(
            initializationVector = unusedInitialInitializationVector,
            encryptedValue = unusedInitialEncryptedValue,
            encryptionAlgorithm = mockAES256GCMAlgorithm
        )

        val encryptionKey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes().also {
            // Clear key to make it invalid
            it.clear()
        }

        val unusedJSONSerializable = mockk<TestJSONSerializable>()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { protectedValue.update(encryptionKey, unusedJSONSerializable) }
        }
        assertEquals("The given encryption key can't be used because it is cleared!", exception.message)
    }

    /**
     * Decrypt tests
     */

    @Test
    fun `Decrypt and instantiate a protected value`() {
        val mockAES256GCMAlgorithm = mockk<EncryptionAlgorithm.Symmetric.AES256GCM>()

        val dataCaptureSlot = slot<ByteArray>()
        coEvery { mockAES256GCMAlgorithm.decrypt(initializationVector = any(), encryptionKey = any(), data = capture(dataCaptureSlot)) } answers {
            Success(dataCaptureSlot.captured)
        }

        val unusedInitializationVector = ByteArray(0)
        val encryptedTestJSONSerializable = byteArrayOf(123, 34, 116, 101, 115, 116, 70, 105, 101, 108, 100, 34, 58, 34, 116, 101, 115, 116, 86, 97, 108, 117, 101, 34, 125)
        val protectedValue = createInstanceForTesting<TestJSONSerializable>(
            initializationVector = unusedInitializationVector,
            encryptedValue = encryptedTestJSONSerializable,
            encryptionAlgorithm = mockAES256GCMAlgorithm
        )

        val unusedEncryptionKey = createNonClearedEncryptionKey()
        val decryptedTestJSONSerializable = runBlocking { protectedValue.decrypt(unusedEncryptionKey, TestJSONSerializable.Deserializer).resultOrThrowException() }

        assertEquals("testValue", decryptedTestJSONSerializable.testField)
    }

    @Test
    fun `Decrypt a protected value with a cleared key throws an exception`() {
        val unusedInitialInitializationVector = ByteArray(0)
        val unusedInitialEncryptedValue = ByteArray(0)

        val unusedUpdatedInitializationVector = ByteArray(0)
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(unusedUpdatedInitializationVector)

        val protectedValue = createInstanceForTesting<TestJSONSerializable>(
            initializationVector = unusedInitialInitializationVector,
            encryptedValue = unusedInitialEncryptedValue,
            encryptionAlgorithm = mockAES256GCMAlgorithm
        )

        val encryptionKey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes().also {
            // Clear key to make it invalid
            it.clear()
        }

        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { protectedValue.decrypt(encryptionKey, TestJSONSerializable.Deserializer) }
        }
        assertEquals("The given encryption key can't be used because it is cleared!", exception.message)
    }

    /**
     * Copy test
     */

    @Test
    fun `Copy a ProtectedValue and expect the references has been changed`() {
        val protectedValue = createSimpleTestProtectedValue(
            initializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes(),
            encryptedValue = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        )

        val protectedValueCopy = protectedValue.copy()

        assertTrue(protectedValue !== protectedValueCopy)
        assertTrue(protectedValue.initializationVector !== protectedValueCopy.initializationVector)
        assertTrue(protectedValue.encryptedValue !== protectedValueCopy.encryptedValue)

        // The encryption algorithm holds no data and is an object
        assertTrue(protectedValue.encryptionAlgorithm === protectedValueCopy.encryptionAlgorithm)
    }

    companion object {
        /**
         * Create a simple `ProtectedValue` with given type and pre-set `encryptionAlgorithm`.
         */
        private fun createSimpleTestProtectedValue(
            initializationVector: ByteArray,
            encryptedValue: ByteArray,
            encryptionAlgorithm: EncryptionAlgorithm.Symmetric = EncryptionAlgorithm.Symmetric.AES256GCM
        ): ProtectedValue<JSONSerializable> {
            return createInstanceForTesting(initializationVector, encryptedValue, encryptionAlgorithm)
        }

        /**
         * Creates a mock `EncryptionAlgorithm.Symmetric.AES256GCM` that returns always the given initialization vector and does NOT encrypt (input data == output data).
         */
        private fun createMockAlgorithmAES256GCMWithoutEncryption(generatedInitializationVector: ByteArray, shouldEncryptionFail: Boolean = false): EncryptionAlgorithm.Symmetric.AES256GCM {
            val mockAES256GCMAlgorithm = mockk<EncryptionAlgorithm.Symmetric.AES256GCM>()
            every { mockAES256GCMAlgorithm.stringRepresentation } returns EncryptionAlgorithm.Symmetric.AES256GCM.stringRepresentation
            coEvery { mockAES256GCMAlgorithm.generateInitializationVector() } returns Success(generatedInitializationVector)

            val dataCaptureSlot = slot<ByteArray>()
            coEvery { mockAES256GCMAlgorithm.encrypt(initializationVector = any(), encryptionKey = any(), data = capture(dataCaptureSlot)) } answers {
                if (shouldEncryptionFail) {
                    Failure(EncryptionFailedException())
                } else {
                    Success(dataCaptureSlot.captured)
                }
            }

            return mockAES256GCMAlgorithm
        }
    }
}

private class EncryptionFailedException : Exception()

private fun createNonClearedEncryptionKey(): ByteArray {
    return ByteArray(1) { 1 }
}

private class TestJSONSerializable(val testField: String) : JSONSerializable {
    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_TEST_FIELD, testField)
        }
    }

    object Deserializer : JSONSerializableDeserializer<TestJSONSerializable>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): TestJSONSerializable {
            return TestJSONSerializable(
                jsonObject.getString(SERIALIZATION_KEY_TEST_FIELD)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_TEST_FIELD = "testField"
    }
}