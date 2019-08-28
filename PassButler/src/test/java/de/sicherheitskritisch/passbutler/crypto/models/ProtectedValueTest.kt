package de.sicherheitskritisch.passbutler.crypto.models

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONSerializableDeserializer
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProtectedValueTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun unsetUp() {
        unmockkAll()
    }

    /**
     * Serialization and deserialization tests
     */

    @Test
    fun `Serialize a protected value with short encrypted value and deserialize it than`() {
        val protectedValueReference = createTestProtectedValue(
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
        val protectedValueReference = createTestProtectedValue(
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
    fun `Create a protected value returns null if the encryption failed`() {
        val initializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(initializationVector, true)

        val unusedEncryptionKey = ByteArray(0)
        val testJSONSerializable = TestJSONSerializable("testValue")

        val protectedValue = ProtectedValue.create(mockAES256GCMAlgorithm, unusedEncryptionKey, testJSONSerializable)
        assertEquals(null, protectedValue)
    }

    @Test
    fun `Create a protected value and expect the given initial values`() {
        val initializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(initializationVector)

        val unusedEncryptionKey = ByteArray(0)
        val testJSONSerializable = TestJSONSerializable("testValue")

        val protectedValue = ProtectedValue.create(mockAES256GCMAlgorithm, unusedEncryptionKey, testJSONSerializable)!!

        assertArrayEquals(initializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.encryptionAlgorithm)

        // Create with `testJSONSerializable.serialize().toString().toByteArray(Charsets.UTF_8)`
        assertArrayEquals(byteArrayOf(123, 34, 116, 101, 115, 116, 70, 105, 101, 108, 100, 34, 58, 34, 116, 101, 115, 116, 86, 97, 108, 117, 101, 34, 125), protectedValue.encryptedValue)
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
        val protectedValue = createTestProtectedValue(
            initializationVector = initialInitializationVector,
            encryptionAlgorithm = mockAES256GCMAlgorithm,
            encryptedValue = initialEncryptedValue
        )

        val unusedEncryptionKey = ByteArray(0)
        val updatedJSONSerializable = TestJSONSerializable("testValue")
        protectedValue.update(unusedEncryptionKey, updatedJSONSerializable)

        assertArrayEquals(updatedInitializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.encryptionAlgorithm)
        assertArrayEquals(byteArrayOf(123, 34, 116, 101, 115, 116, 70, 105, 101, 108, 100, 34, 58, 34, 116, 101, 115, 116, 86, 97, 108, 117, 101, 34, 125), protectedValue.encryptedValue)
    }

    @Test
    fun `If it fails to update a protected value, the initialization vector and updated encrypted value are not changed`() {
        val updatedInitializationVector = "bbbbbbbbbbbbbbbbbbbbbbbb".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(updatedInitializationVector, true)

        val initialInitializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val initialEncryptedValue = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val protectedValue = createTestProtectedValue(
            initializationVector = initialInitializationVector,
            encryptionAlgorithm = mockAES256GCMAlgorithm,
            encryptedValue = initialEncryptedValue
        )

        val unusedEncryptionKey = ByteArray(0)
        val updatedJSONSerializable = TestJSONSerializable("testValue")
        protectedValue.update(unusedEncryptionKey, updatedJSONSerializable)

        assertArrayEquals(initialInitializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.encryptionAlgorithm)
        assertArrayEquals(initialEncryptedValue, protectedValue.encryptedValue)
    }

    /**
     * Decrypt tests
     */

    @Test
    fun `Decrypt and instantiate a protected value`() {
        val mockAES256GCMAlgorithm = mockk<EncryptionAlgorithm.Symmetric.AES256GCM>()

        val dataCaptureSlot = slot<ByteArray>()
        every { EncryptionAlgorithm.Symmetric.AES256GCM.decrypt(initializationVector = any(), encryptionKey = any(), data = capture(dataCaptureSlot)) } answers {
            dataCaptureSlot.captured
        }

        val unusedInitializationVector = ByteArray(0)
        val encryptedTestJSONSerializable = byteArrayOf(123, 34, 116, 101, 115, 116, 70, 105, 101, 108, 100, 34, 58, 34, 116, 101, 115, 116, 86, 97, 108, 117, 101, 34, 125)
        val protectedValue = ProtectedValue<TestJSONSerializable>(
            initializationVector = unusedInitializationVector,
            encryptionAlgorithm = mockAES256GCMAlgorithm,
            encryptedValue = encryptedTestJSONSerializable
        )

        val unusedEncryptionKey = ByteArray(0)
        val decryptedTestJSONSerializable = protectedValue.decrypt(unusedEncryptionKey, TestJSONSerializable.Deserializer)

        assertEquals("testValue", decryptedTestJSONSerializable?.testField)
    }

    companion object {
        /**
         * Create a simple `ProtectedValue` with given argument values and pre-set encryptionAlgorithm.
         */
        private fun createTestProtectedValue(
            initializationVector: ByteArray,
            encryptionAlgorithm: EncryptionAlgorithm.Symmetric = EncryptionAlgorithm.Symmetric.AES256GCM,
            encryptedValue: ByteArray
        ): ProtectedValue<JSONSerializable> {
            return ProtectedValue(initializationVector, encryptionAlgorithm, encryptedValue)
        }

        /**
         * Creates a mock `EncryptionAlgorithm.Symmetric.AES256GCM` that returns always the given initialization vector and does NOT encrypt (input data == output data).
         */
        private fun createMockAlgorithmAES256GCMWithoutEncryption(generatedInitializationVector: ByteArray, shouldEncryptionFail: Boolean = false): EncryptionAlgorithm.Symmetric.AES256GCM {
            val mockAES256GCMAlgorithm = mockk<EncryptionAlgorithm.Symmetric.AES256GCM>()
            every { mockAES256GCMAlgorithm.stringRepresentation } returns EncryptionAlgorithm.Symmetric.AES256GCM.stringRepresentation
            every { EncryptionAlgorithm.Symmetric.AES256GCM.generateInitializationVector() } returns generatedInitializationVector

            val dataCaptureSlot = slot<ByteArray>()
            every { EncryptionAlgorithm.Symmetric.AES256GCM.encrypt(initializationVector = any(), encryptionKey = any(), data = capture(dataCaptureSlot)) } answers {
                if (shouldEncryptionFail) {
                    throw EncryptionAlgorithm.EncryptionFailedException()
                } else {
                    dataCaptureSlot.captured
                }
            }

            return mockAES256GCMAlgorithm
        }
    }
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