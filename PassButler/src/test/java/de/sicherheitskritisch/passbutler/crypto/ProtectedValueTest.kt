package de.sicherheitskritisch.passbutler.crypto

import android.util.Log
import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
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
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun unsetUp() {
        unmockkAll()
    }

    @Test
    fun `Serialize a protected value with short encrypted value and deserialize it than`() {
        val protectedValueReference = createTestProtectedValue(
            initializationVector = "1310aadeaa489ae84125c36a".hexToBytes(),
            encryptedValue = "4e692fce708b1b759cf61beb5c4e55a3a22d749c5839b3654d6cbe2299b3c28a".hexToBytes()
        )

        val serializedProtectedValueReference = JSONObject().apply {
            putString("initializationVector", "ExCq3qpImuhBJcNq")
            putString("algorithm", "AES-256-GCM")
            putString("encryptedValue", "TmkvznCLG3Wc9hvrXE5Vo6ItdJxYObNlTWy+Ipmzwoo=")
        }

        val serializedProtectedValue = protectedValueReference.serialize()
        assertJSONObjectEquals(serializedProtectedValueReference, serializedProtectedValue)

        val deserializedProtectedValue = ProtectedValue.deserialize<JSONSerializable>(serializedProtectedValue)
        assertEquals(protectedValueReference, deserializedProtectedValue)
    }

    @Test
    fun `Serialize a protected value with longer encrypted value and deserialize it than`() {
        val protectedValueReference = createTestProtectedValue(
            initializationVector = "b263e025c3d0e60765e7eeba".hexToBytes(),
            encryptedValue = "0664c21c4485b0a37ebd3d0c5cba77c88ed4be3d8035b40390d8c32c6eaaa12dfd3d6fc19fa6b0d12092e9384f26e60747019c0294de426574b8a3d1dab2f5802a4db735952300b5da".hexToBytes()
        )

        val serializedProtectedValueReference = JSONObject().apply {
            putString("initializationVector", "smPgJcPQ5gdl5+66")
            putString("algorithm", "AES-256-GCM")
            putString("encryptedValue", "BmTCHESFsKN+vT0MXLp3yI7Uvj2ANbQDkNjDLG6qoS39PW/Bn6aw0SCS6ThPJuYHRwGcApTeQmV0uKPR2rL1gCpNtzWVIwC12g==")
        }

        val serializedProtectedValue = protectedValueReference.serialize()
        assertJSONObjectEquals(serializedProtectedValueReference, serializedProtectedValue)

        val deserializedProtectedValue = ProtectedValue.deserialize<JSONSerializable>(serializedProtectedValue)
        assertEquals(protectedValueReference, deserializedProtectedValue)
    }

    @Test
    fun `Deserialize a protected value returns null if the deserialization failed`() {
        val invalidSerializedProtectedValue = JSONObject()

        val deserializedProtectedValue = ProtectedValue.deserialize<JSONSerializable>(invalidSerializedProtectedValue)
        assertEquals(null, deserializedProtectedValue)
    }

    @Test
    fun `Create a protected value returns null if the encryption failed`() {
        val initializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(initializationVector, true)

        val unusedEncryptionKey = ByteArray(0)
        val testJSONSerializable = TestJSONSerializable()

        val protectedValue = ProtectedValue.create(mockAES256GCMAlgorithm, unusedEncryptionKey, testJSONSerializable)
        assertEquals(null, protectedValue)
    }

    @Test
    fun `Create a protected value and expect the given initial values`() {
        val initializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(initializationVector)

        val unusedEncryptionKey = ByteArray(0)
        val testJSONSerializable = TestJSONSerializable()

        val protectedValue = ProtectedValue.create(mockAES256GCMAlgorithm, unusedEncryptionKey, testJSONSerializable)!!

        assertArrayEquals(initializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.algorithm)
        assertArrayEquals(TestJSONSerializable.SERIALIZED_OBJECT_BYTE_ARRAY, protectedValue.encryptedValue)
    }

    @Test
    fun `Update a protected value and expect an updated initialization vector and updated encrypted value`() {
        val updatedInitializationVector = "bbbbbbbbbbbbbbbbbbbbbbbb".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(updatedInitializationVector)

        val initialInitializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val protectedValue = createTestProtectedValue(
            initializationVector = initialInitializationVector,
            algorithm = mockAES256GCMAlgorithm,
            encryptedValue = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        )

        val unusedEncryptionKey = ByteArray(0)
        val updatedJSONSerializable = TestJSONSerializable()
        protectedValue.update(unusedEncryptionKey, updatedJSONSerializable)

        assertArrayEquals(updatedInitializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.algorithm)
        assertArrayEquals(TestJSONSerializable.SERIALIZED_OBJECT_BYTE_ARRAY, protectedValue.encryptedValue)
    }

    @Test
    fun `If it fails to update a protected value, the initialization vector and updated encrypted value are not changed`() {
        val updatedInitializationVector = "bbbbbbbbbbbbbbbbbbbbbbbb".hexToBytes()
        val mockAES256GCMAlgorithm = createMockAlgorithmAES256GCMWithoutEncryption(updatedInitializationVector, true)

        val initialInitializationVector = "aaaaaaaaaaaaaaaaaaaaaaaa".hexToBytes()
        val initialEncryptedValue = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val protectedValue = createTestProtectedValue(
            initializationVector = initialInitializationVector,
            algorithm = mockAES256GCMAlgorithm,
            encryptedValue = initialEncryptedValue
        )

        val unusedEncryptionKey = ByteArray(0)
        val updatedJSONSerializable = TestJSONSerializable()
        protectedValue.update(unusedEncryptionKey, updatedJSONSerializable)

        assertArrayEquals(initialInitializationVector, protectedValue.initializationVector)
        assertEquals(mockAES256GCMAlgorithm, protectedValue.algorithm)
        assertArrayEquals(initialEncryptedValue, protectedValue.encryptedValue)
    }
}

/**
 * Create a simple `ProtectedValue` with given argument values and pre-set algorithm.
 */
private fun createTestProtectedValue(initializationVector: ByteArray, algorithm: Algorithm = Algorithm.AES256GCM, encryptedValue: ByteArray): ProtectedValue<JSONSerializable> {
    return ProtectedValue(initializationVector, algorithm, encryptedValue)
}

/**
 * Creates a mock `Algorithm.AES256GCM` that returns always the given initialization vector and does NOT encrypt (input data == output data).
 */
private fun createMockAlgorithmAES256GCMWithoutEncryption(generatedInitializationVector: ByteArray, shouldEncryptionFail: Boolean = false): Algorithm.AES256GCM {
    val mockAES256GCMAlgorithm = mockk<Algorithm.AES256GCM>()
    every { mockAES256GCMAlgorithm.stringRepresentation } returns Algorithm.AES256GCM.stringRepresentation
    every { mockAES256GCMAlgorithm.generateInitializationVector() } returns generatedInitializationVector

    val dataCaptureSlot = slot<ByteArray>()
    every { mockAES256GCMAlgorithm.encrypt(initializationVector = any(), encryptionKey = any(), data = capture(dataCaptureSlot)) } answers {
        if (shouldEncryptionFail) {
            throw Algorithm.EncryptionFailedException()
        } else {
            dataCaptureSlot.captured
        }
    }

    return mockAES256GCMAlgorithm
}

private class TestJSONSerializable : JSONSerializable {
    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString("teststring", "testvalue")
        }
    }

    companion object {
        // Created with `testJSONSerializable.serialize().toByteArray(Charsets.UTF_8)`
        val SERIALIZED_OBJECT_BYTE_ARRAY = byteArrayOf(123, 34, 116, 101, 115, 116, 115, 116, 114, 105, 110, 103, 34, 58, 34, 116, 101, 115, 116, 118, 97, 108, 117, 101, 34, 125)
    }
}