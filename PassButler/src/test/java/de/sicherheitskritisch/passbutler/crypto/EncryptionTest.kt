package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.toHexString
import de.sicherheitskritisch.passbutler.hexToBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SymmetricEncryptionTest {

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

    companion object {

        private val invalidTestVectors = mapOf(
            "emptyInitializationVector" to SymmetricTestVector(
                key = "0000000000000000000000000000000000000000000000000000000000000000",
                initializationVector = "",
                plainText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                cipherText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                tag = "00000000000000000000000000000000"
            ),

            "tooLongInitializationVector" to SymmetricTestVector(
                key = "0000000000000000000000000000000000000000000000000000000000000000",
                initializationVector = "000000000000000000000000AA",
                plainText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                cipherText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                tag = "00000000000000000000000000000000"
            ),

            "emptyKey" to SymmetricTestVector(
                key = "",
                initializationVector = "000000000000000000000000",
                plainText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                cipherText = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                tag = "00000000000000000000000000000000"
            ),

            "tooLongKey" to SymmetricTestVector(
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
            SymmetricTestVector(
                key = "F5A2B27C74355872EB3EF6C5FEAFAA740E6AE990D9D48C3BD9BB8235E589F010",
                initializationVector = "58D2240F580A31C1D24948E9",
                plainText = "",
                cipherText = "",
                tag = "15E051A5E4A5F5DA6CEA92E2EBEE5BAC"
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

            SymmetricTestVector(
                key = "1FDED32D5999DE4A76E0F8082108823AEF60417E1896CF4218A2FA90F632EC8A",
                initializationVector = "1F3AFA4711E9474F32E70462",
                plainText = "06B2C75853DF9AEB17BEFD33CEA81C630B0FC53667FF45199C629C8E15DCE41E530AA792F796B8138EEAB2E86C7B7BEE1D40B0",
                cipherText = "91FBD061DDC5A7FCC9513FCDFDC9C3A7C5D4D64CEDF6A9C24AB8A77C36EEFBF1C5DC00BC50121B96456C8CD8B6FF1F8B3E480F",
                tag = "30096D340F3D5C42D82A6F475DEF23EB"
            ),

            SymmetricTestVector(
                key = "B405AC89724F8B555BFEE1EAA369CD854003E9FAE415F28C5A199D4D6EFC83D6",
                initializationVector = "CEC71A13B14C4D9BD024EF29",
                plainText = "AB4FD35BEF66ADDFD2856B3881FF2C74FDC09C82ABE339F49736D69B2BD0A71A6B4FE8FC53F50F8B7D6D6D6138AB442C7F653F",
                cipherText = "69A079BCA9A6A26707BBFA7FD83D5D091EDC88A7F7FF08BD8656D8F2C92144FF23400FCB5C370B596AD6711F386E18F2629E76",
                tag = "6D2B7861A3C59BA5A3E3A11C92BB2B14"
            ),

            SymmetricTestVector(
                key = "FAD40C82264DC9B8D9A42C10A234138344B0133A708D8899DA934BFEE2BDD6B8",
                initializationVector = "0DADE2C95A9B85A8D2BC13EF",
                plainText = "664EA95D511B2CFDB9E5FB87EFDD41CBFB88F3FF47A7D2B8830967E39071A89B948754FFB0ED34C357ED6D4B4B2F8A76615C03",
                cipherText = "EA94DCBF52B22226DDA91D9BFC96FB382730B213B66E30960B0D20D2417036CBAA9E359984EEA947232526E175F49739095E69",
                tag = "5CA8905D469FFFEC6FBA7435EBDFFDAF"
            ),

            SymmetricTestVector(
                key = "AA5FCA688CC83283ECF39454679948F4D30AA8CB43DB7CC4DA4EFF1669D6C52F",
                initializationVector = "4B2D7B699A5259F9B541FA49",
                plainText = "C691F3B8F3917EFB76825108C0E37DC33E7A8342764CE68A62A2DC1A5C940594961FCD5C0DF05394A5C0FFF66C254C6B26A549",
                cipherText = "2CD380EBD6B2CF1B80831CFF3D6DC2B6770778AD0D0A91D03EB8553696800F84311D337302519D1036FEAAB8C8EB845882C5F0",
                tag = "5DE4EF67BF8896FBE82C01DCA041D590"
            ),

            SymmetricTestVector(
                key = "1C7690D5D845FCEABBA227B11CA221F4D6D302233641016D9CD3A158C3E36017",
                initializationVector = "93BCA8DE6B11A4830C5F5F64",
                plainText = "3C79A39878A605F3AC63A256F68C8A66369CC3CD7AF680D19692B485A7BA58CE1D536707C55EDA5B256C8B29BBF0B4CBEB4FC4",
                cipherText = "C9E48684DF13AFCCDB1D9CEAA483759022E59C3111188C1ECEB02EAF308035B0428DB826DE862D925A3C55AF0B61FD8F09A74D",
                tag = "8F577E8730C19858CAD8E0124F311DD9"
            )
        )

        private fun encryptAES256GCM(testVector: SymmetricTestVector): String {
            return EncryptionAlgorithm.Symmetric.AES256GCM.encrypt(testVector.initializationVector.hexToBytes(), testVector.key.hexToBytes(), testVector.plainText.hexToBytes()).toHexString()
        }

        private fun decryptAES256GCM(testVector: SymmetricTestVector): String {
            return EncryptionAlgorithm.Symmetric.AES256GCM.decrypt(testVector.initializationVector.hexToBytes(), testVector.key.hexToBytes(), (testVector.cipherText + testVector.tag).hexToBytes()).toHexString()
        }
    }
}

private data class SymmetricTestVector(
    val key: String,
    val initializationVector: String,
    val plainText: String,
    val cipherText: String,
    val tag: String
)
