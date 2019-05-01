package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.assertJSONObjectEquals
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.hexToBytes
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.jupiter.api.Test

class ProtectedValueTest {

    /**
     * Serialization tests
     */

    @Test
    fun `Create a protected value with short encrypted value and serialize it`() {
        val initializationVector = "1310aadeaa489ae84125c36a".hexToBytes()
        val encryptedValue = "4e692fce708b1b759cf61beb5c4e55a3a22d749c5839b3654d6cbe2299b3c28a".hexToBytes()

        val mockAlgorithm = createMockEncryptAlgorithmAES256GCM(initializationVector, encryptedValue)
        val unusedEncryptionKey = ByteArray(0)
        val mockJSONSerializable = createMockJSONSerializable()

        val protectedValue = ProtectedValue.create(mockAlgorithm, unusedEncryptionKey, mockJSONSerializable)!!
        val serializedProtectedValue = protectedValue.serialize()

        assertJSONObjectEquals(
            JSONObject().apply {
                putString("initializationVector", "ExCq3qpImuhBJcNq")
                putString("algorithm", "AES-256-GCM")
                putString("encryptedValue", "TmkvznCLG3Wc9hvrXE5Vo6ItdJxYObNlTWy+Ipmzwoo=")
            },
            serializedProtectedValue
        )
    }

    @Test
    fun `Create a protected value with long encrypted value and serialize it`() {
        val initializationVector = "b263e025c3d0e60765e7eeba".hexToBytes()
        val encryptedValue = "efd78a8f3216381554fce904e25c2cb91db04ecb59c375d2483624d6c7bbd2c0a97e319e4f5e6fa40cf7607b058c1fe278d907f6912258cca305a744b6a5444f4b8fd15f59fbae86b46232835c34937e38466ae24ff33d3562cbe62771721ef4b96a93d1fd7f7519a3b2878daf9a01f6e4972a0ee8cc73f7fd086a442a02db860b9f778e28595cf96a227a56ab8cb60dd6a140aca2d2693c08bdf244daf1fe7ca270e263a9f9657de6fad58f22dc691aac0ec3a6390867c0142b120faa9dee620df912800725bd3eccb6198fc621cad3a1092708861febe07d136d89554f2d18b9487f085da83596b22a03f121580e721653716ad07e0c51ef29".hexToBytes()

        val mockAlgorithm = createMockEncryptAlgorithmAES256GCM(initializationVector, encryptedValue)
        val unusedEncryptionKey = ByteArray(0)
        val mockJSONSerializable = createMockJSONSerializable()

        val protectedValue = ProtectedValue.create(mockAlgorithm, unusedEncryptionKey, mockJSONSerializable)!!
        val serializedProtectedValue = protectedValue.serialize()

        assertJSONObjectEquals(
            JSONObject().apply {
                putString("initializationVector", "smPgJcPQ5gdl5+66")
                putString("algorithm", "AES-256-GCM")
                putString("encryptedValue", "79eKjzIWOBVU/OkE4lwsuR2wTstZw3XSSDYk1se70sCpfjGeT15vpAz3YHsFjB/ieNkH9pEiWMyjBadEtqVET0uP0V9Z+66GtGIyg1w0k344RmriT/M9NWLL5idxch70uWqT0f1/dRmjsoeNr5oB9uSXKg7ozHP3/QhqRCoC24YLn3eOKFlc+WoielarjLYN1qFArKLSaTwIvfJE2vH+fKJw4mOp+WV95vrVjyLcaRqsDsOmOQhnwBQrEg+qne5iDfkSgAclvT7MthmPxiHK06EJJwiGH+vgfRNtiVVPLRi5SH8IXag1lrIqA/EhWA5yFlNxatB+DFHvKQ==")
            },
            serializedProtectedValue
        )
    }
}

private fun createMockEncryptAlgorithmAES256GCM(generatedInitializationVector: ByteArray, encryptedValue: ByteArray): Algorithm.AES256GCM {
    val mockAlgorithm = mockk<Algorithm.AES256GCM>()
    every { mockAlgorithm.stringRepresentation } returns Algorithm.AES256GCM.stringRepresentation
    every { mockAlgorithm.generateInitializationVector() } returns generatedInitializationVector
    every { mockAlgorithm.encrypt(any(), any(), any()) } returns encryptedValue

    return mockAlgorithm
}

private fun createMockJSONSerializable(): JSONSerializable {
    val mockJSONSerializable = mockk<JSONSerializable>()
    every { mockJSONSerializable.serialize() } returns JSONObject()
    return mockJSONSerializable
}

private class TestJSONSerializable : JSONSerializable {
    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString("teststring", "testvalue")
        }
    }
}