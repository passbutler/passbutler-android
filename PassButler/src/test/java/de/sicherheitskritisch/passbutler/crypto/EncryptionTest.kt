package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.hexToBytes
import de.sicherheitskritisch.passbutler.toHexString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EncryptionTest {

    /**
     * AES-256-GCM encryption tests
     */

    @Test
    fun `Encrypt with an empty initialization vector throws an exception`() {
        val testVector = invalidTestVectors.getValue("tooLongInitializationVector")

        val exception = assertThrows(EncryptionAlgorithm.EncryptionFailedException::class.java) {
            encryptAES256GCM(testVector)
        }

        assertEquals("The initialization vector must be 96 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Encrypt with a too long initialization vector throws an exception`() {
        val testVector = invalidTestVectors.getValue("emptyInitializationVector")

        val exception = assertThrows(EncryptionAlgorithm.EncryptionFailedException::class.java) {
            encryptAES256GCM(testVector)
        }

        assertEquals("The initialization vector must be 96 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Encrypt with an empty key throws an exception`() {
        val testVector = invalidTestVectors.getValue("emptyKey")

        val exception = assertThrows(EncryptionAlgorithm.EncryptionFailedException::class.java) {
            encryptAES256GCM(testVector)
        }

        assertEquals("The encryption key must be 256 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Encrypt with a too long key throws an exception`() {
        val testVector = invalidTestVectors.getValue("tooLongKey")

        val exception = assertThrows(EncryptionAlgorithm.EncryptionFailedException::class.java) {
            encryptAES256GCM(testVector)
        }

        assertEquals("The encryption key must be 256 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector (no plain text)`() {
        val testVector = validTestVectors[0]

        val encryptionResult = encryptAES256GCM(testVector)
        assertEquals(testVector.cipherText + testVector.tag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 1`() {
        val testVector = validTestVectors[1]

        val encryptionResult = encryptAES256GCM(testVector)
        assertEquals(testVector.cipherText + testVector.tag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 2`() {
        val testVector = validTestVectors[2]

        val encryptionResult = encryptAES256GCM(testVector)
        assertEquals(testVector.cipherText + testVector.tag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 3`() {
        val testVector = validTestVectors[3]

        val encryptionResult = encryptAES256GCM(testVector)
        assertEquals(testVector.cipherText + testVector.tag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 4`() {
        val testVector = validTestVectors[4]

        val encryptionResult = encryptAES256GCM(testVector)
        assertEquals(testVector.cipherText + testVector.tag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 5`() {
        val testVector = validTestVectors[5]

        val encryptionResult = encryptAES256GCM(testVector)
        assertEquals(testVector.cipherText + testVector.tag, encryptionResult)
    }

    /**
     * AES-256-GCM decryption tests
     */

    @Test
    fun `Decrypt with an empty initialization vector throws an exception`() {
        val testVector = invalidTestVectors.getValue("tooLongInitializationVector")

        val exception = assertThrows(EncryptionAlgorithm.DecryptionFailedException::class.java) {
            decryptAES256GCM(testVector)
        }

        assertEquals("The initialization vector must be 96 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Decrypt with a too long initialization vector throws an exception`() {
        val testVector = invalidTestVectors.getValue("emptyInitializationVector")

        val exception = assertThrows(EncryptionAlgorithm.DecryptionFailedException::class.java) {
            decryptAES256GCM(testVector)
        }

        assertEquals("The initialization vector must be 96 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Decrypt with an empty key throws an exception`() {
        val testVector = invalidTestVectors.getValue("emptyKey")

        val exception = assertThrows(EncryptionAlgorithm.DecryptionFailedException::class.java) {
            decryptAES256GCM(testVector)
        }

        assertEquals("The encryption key must be 256 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Decrypt with a too long key throws an exception`() {
        val testVector = invalidTestVectors.getValue("tooLongKey")

        val exception = assertThrows(EncryptionAlgorithm.DecryptionFailedException::class.java) {
            decryptAES256GCM(testVector)
        }

        assertEquals("The encryption key must be 256 bits long!", (exception.cause as IllegalArgumentException).message)
    }

    @Test
    fun `Decrypt AES-256-GCM test vector (no plain text)`() {
        val testVector = validTestVectors[0]

        val decryptionResult = decryptAES256GCM(testVector)
        assertEquals(testVector.plainText, decryptionResult)
    }

    @Test
    fun `Decrypt AES-256-GCM test vector 1`() {
        val testVector = validTestVectors[1]

        val decryptionResult = decryptAES256GCM(testVector)
        assertEquals(testVector.plainText, decryptionResult)
    }

    @Test
    fun `Decrypt AES-256-GCM test vector 2`() {
        val testVector = validTestVectors[2]

        val decryptionResult = decryptAES256GCM(testVector)
        assertEquals(testVector.plainText, decryptionResult)
    }

    @Test
    fun `Decrypt AES-256-GCM test vector 3`() {
        val testVector = validTestVectors[3]

        val decryptionResult = decryptAES256GCM(testVector)
        assertEquals(testVector.plainText, decryptionResult)
    }

    @Test
    fun `Decrypt AES-256-GCM test vector 4`() {
        val testVector = validTestVectors[4]

        val decryptionResult = decryptAES256GCM(testVector)
        assertEquals(testVector.plainText, decryptionResult)
    }

    @Test
    fun `Decrypt AES-256-GCM test vector 5`() {
        val testVector = validTestVectors[5]

        val decryptionResult = decryptAES256GCM(testVector)
        assertEquals(testVector.plainText, decryptionResult)
    }
}

private val invalidTestVectors = mapOf(
    "emptyInitializationVector" to TestVector(
        key = "0000000000000000000000000000000000000000000000000000000000000000",
        initializationVector = "",
        plainText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        cipherText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        tag = "00000000000000000000000000000000"
    ),

    "tooLongInitializationVector" to TestVector(
        key = "0000000000000000000000000000000000000000000000000000000000000000",
        initializationVector = "000000000000000000000000AA",
        plainText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        cipherText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        tag = "00000000000000000000000000000000"
    ),

    "emptyKey" to TestVector(
        key = "",
        initializationVector = "000000000000000000000000",
        plainText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        cipherText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        tag = "00000000000000000000000000000000"
    ),

    "tooLongKey" to TestVector(
        key = "0000000000000000000000000000000000000000000000000000000000000000AA",
        initializationVector = "000000000000000000000000",
        plainText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        cipherText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        tag = "00000000000000000000000000000000"
    )
)

/**
 * Test vectors took from <https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Algorithm-Validation-Program/documents/mac/gcmtestvectors.zip>
 */
private val validTestVectors = listOf(

    /*
     * [Keylen = 256]
     * [IVlen = 96]
     * [PTlen = 0]
     * [AADlen = 0]
     * [Taglen = 128]
     *
     * (from file `gcmDecrypt256.rsp`)
    */
    TestVector(
        key = "f5a2b27c74355872eb3ef6c5feafaa740e6ae990d9d48c3bd9bb8235e589f010",
        initializationVector = "58d2240f580a31c1d24948e9",
        plainText = "",
        cipherText = "",
        tag = "15e051a5e4a5f5da6cea92e2ebee5bac"
    ),

    /*
     * [Keylen = 256]
     * [IVlen = 96]
     * [PTlen = 408]
     * [AADlen = 0]
     * [Taglen = 128]
     *
     * (from file `gcmEncryptExtIV256.rsp`)
     */

    TestVector(
        key = "1fded32d5999de4a76e0f8082108823aef60417e1896cf4218a2fa90f632ec8a",
        initializationVector = "1f3afa4711e9474f32e70462",
        plainText = "06b2c75853df9aeb17befd33cea81c630b0fc53667ff45199c629c8e15dce41e530aa792f796b8138eeab2e86c7b7bee1d40b0",
        cipherText = "91fbd061ddc5a7fcc9513fcdfdc9c3a7c5d4d64cedf6a9c24ab8a77c36eefbf1c5dc00bc50121b96456c8cd8b6ff1f8b3e480f",
        tag = "30096d340f3d5c42d82a6f475def23eb"
    ),

    TestVector(
        key = "b405ac89724f8b555bfee1eaa369cd854003e9fae415f28c5a199d4d6efc83d6",
        initializationVector = "cec71a13b14c4d9bd024ef29",
        plainText = "ab4fd35bef66addfd2856b3881ff2c74fdc09c82abe339f49736d69b2bd0a71a6b4fe8fc53f50f8b7d6d6d6138ab442c7f653f",
        cipherText = "69a079bca9a6a26707bbfa7fd83d5d091edc88a7f7ff08bd8656d8f2c92144ff23400fcb5c370b596ad6711f386e18f2629e76",
        tag = "6d2b7861a3c59ba5a3e3a11c92bb2b14"
    ),

    TestVector(
        key = "fad40c82264dc9b8d9a42c10a234138344b0133a708d8899da934bfee2bdd6b8",
        initializationVector = "0dade2c95a9b85a8d2bc13ef",
        plainText = "664ea95d511b2cfdb9e5fb87efdd41cbfb88f3ff47a7d2b8830967e39071a89b948754ffb0ed34c357ed6d4b4b2f8a76615c03",
        cipherText = "ea94dcbf52b22226dda91d9bfc96fb382730b213b66e30960b0d20d2417036cbaa9e359984eea947232526e175f49739095e69",
        tag = "5ca8905d469fffec6fba7435ebdffdaf"
    ),

    TestVector(
        key = "aa5fca688cc83283ecf39454679948f4d30aa8cb43db7cc4da4eff1669d6c52f",
        initializationVector = "4b2d7b699a5259f9b541fa49",
        plainText = "c691f3b8f3917efb76825108c0e37dc33e7a8342764ce68a62a2dc1a5c940594961fcd5c0df05394a5c0fff66c254c6b26a549",
        cipherText = "2cd380ebd6b2cf1b80831cff3d6dc2b6770778ad0d0a91d03eb8553696800f84311d337302519d1036feaab8c8eb845882c5f0",
        tag = "5de4ef67bf8896fbe82c01dca041d590"
    ),

    TestVector(
        key = "1c7690d5d845fceabba227b11ca221f4d6d302233641016d9cd3a158c3e36017",
        initializationVector = "93bca8de6b11a4830c5f5f64",
        plainText = "3c79a39878a605f3ac63a256f68c8a66369cc3cd7af680d19692b485a7ba58ce1d536707c55eda5b256c8b29bbf0b4cbeb4fc4",
        cipherText = "c9e48684df13afccdb1d9ceaa483759022e59c3111188c1eceb02eaf308035b0428db826de862d925a3c55af0b61fd8f09a74d",
        tag = "8f577e8730c19858cad8e0124f311dd9"
    )
)

private fun encryptAES256GCM(testVector: TestVector): String {
    return EncryptionAlgorithm.AES256GCM.encrypt(testVector.initializationVector.hexToBytes(), testVector.key.hexToBytes(), testVector.plainText.hexToBytes()).toHexString()
}

private fun decryptAES256GCM(testVector: TestVector): String {
    return EncryptionAlgorithm.AES256GCM.decrypt(testVector.initializationVector.hexToBytes(), testVector.key.hexToBytes(), (testVector.cipherText + testVector.tag).hexToBytes()).toHexString()
}

private data class TestVector(
    val key: String,
    val initializationVector: String,
    val plainText: String,
    val cipherText: String,
    val tag: String
)
