package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.assertEqualsIgnoringCase
import de.sicherheitskritisch.passbutler.base.toHexString
import de.sicherheitskritisch.passbutler.hexToBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPrivateKeySpec


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
    fun `Encrypt AES-256-GCM valid test vectors`() {
        validTestVectors.forEach { testVector ->
            val encryptionResult = encryptAES256GCM(testVector)
            assertEquals(testVector.cipherText + testVector.tag, encryptionResult)
        }

        assertEquals(8, validTestVectors.size)
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
    fun `Decrypt AES-256-GCM valid test vectors`() {
        validTestVectors.forEach { testVector ->
            val decryptionResult = decryptAES256GCM(testVector)
            assertEquals(testVector.plainText, decryptionResult)
        }

        assertEquals(8, validTestVectors.size)
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
         * Test vectors took from: <https://github.com/pyca/cryptography/blob/2.4.x/vectors/cryptography_vectors/ciphers/AES/GCM/gcmDecrypt256.rsp>
         *
         * The vectors were chosen using the following scheme:
         * [Keylen = 256]
         * [IVlen = 96]
         * [PTlen = N]
         * [AADlen = 0]
         * [Taglen = 128]
         */
        private val validTestVectors = listOf(

            /*
             * Key with plain text length == 0
             */

            SymmetricTestVector(
                key = "f5a2b27c74355872eb3ef6c5feafaa740e6ae990d9d48c3bd9bb8235e589f010",
                initializationVector = "58d2240f580a31c1d24948e9",
                plainText = "",
                cipherText = "",
                tag = "15e051a5e4a5f5da6cea92e2ebee5bac"
            ),

            SymmetricTestVector(
                key = "c1d6162b585e2bac14d554d5675c6ddaa6b93be2eb07f8df86c9bb30f93ae688",
                initializationVector = "f04dfce5c8e7713c71a70cc9",
                plainText = "",
                cipherText = "",
                tag = "37fb4f33c82f6fce0c562896b3e10fc2"
            ),

            /*
             * Keys with plain text length == 128
             */

            SymmetricTestVector(
                key = "4c8ebfe1444ec1b2d503c6986659af2c94fafe945f72c1e8486a5acfedb8a0f8",
                initializationVector = "473360e0ad24889959858995",
                plainText = "7789b41cb3ee548814ca0b388c10b343",
                cipherText = "d2c78110ac7e8f107c0df0570bd7c90c",
                tag = "c26a379b6d98ef2852ead8ce83a833a7"
            ),

            SymmetricTestVector(
                key = "3934f363fd9f771352c4c7a060682ed03c2864223a1573b3af997e2ababd60ab",
                initializationVector = "efe2656d878c586e41c539c4",
                plainText = "697aff2d6b77e5ed6232770e400c1ead",
                cipherText = "e0de64302ac2d04048d65a87d2ad09fe",
                tag = "33cbd8d2fb8a3a03e30c1eb1b53c1d99"
            ),

            /*
             * Keys with plain text length == 256
             */

            SymmetricTestVector(
                key = "c3d99825f2181f4808acd2068eac7441a65bd428f14d2aab43fefc0129091139",
                initializationVector = "cafabd9672ca6c79a2fbdc22",
                plainText = "25431587e9ecffc7c37f8d6d52a9bc3310651d46fb0e3bad2726c8f2db653749",
                cipherText = "84e5f23f95648fa247cb28eef53abec947dbf05ac953734618111583840bd980",
                tag = "79651c875f7941793d42bbd0af1cce7c"
            ),

            SymmetricTestVector(
                key = "5c3bd1986d3c807b0c3ace811e618dbae1693f07145f282d474daaae0b6a1774",
                initializationVector = "3c9e5a952b5009afd3dd1eac",
                plainText = "7adb5cc81adcc3b7561d00972c313bee74b9022c8c035de386f476c8efa15f62",
                cipherText = "ebb8c233496a5bddf70821fb8914ec8aa9633c1fcbc067948fc2d82e8fbe2fbb",
                tag = "55074766eba059eee2af2db30029cf53"
            ),

            /*
             * Keys with plain text length > 256
             */

            SymmetricTestVector(
                key = "4433db5fe066960bdd4e1d4d418b641c14bfcef9d574e29dcd0995352850f1eb",
                initializationVector = "0e396446655582838f27f72f",
                plainText = "d602c06b947abe06cf6aa2c5c1562e29062ad6220da9bc9c25d66a60bd85a80d4fbcc1fb4919b6566be35af9819aba836b8b47",
                cipherText = "b0d254abe43bdb563ead669192c1e57e9a85c51dba0f1c8501d1ce92273f1ce7e140dcfac94757fabb128caad16912cead0607",
                tag = "ffd0b02c92dbfcfbe9d58f7ff9e6f506"
            ),

            SymmetricTestVector(
                key = "f9b70fd065668b9fc4ee7e222f1c4ae27e0a6e37b551e7d5fb58eea40a59fba3",
                initializationVector = "a7f5ddb39b8c62b50b5a8c0c",
                plainText = "6e9c24c172ae8e81e69e797a8bd9f8de4e5e43ccbdeec5a0d0ec1a7b3527384e06129290c5f61fa2f90ae8b03a9402aeb0b6ce",
                cipherText = "0d6dcdf0820f546d54f5476f49bbf1cfafae3b5c7cb0875c826757650864f99d74ee4073651eed0dbaf5789d211c1be5579843",
                tag = "31efc69daae6f7f0067fd6e969bd9240"
            )
        )

        private fun encryptAES256GCM(testVector: SymmetricTestVector): String {
            return EncryptionAlgorithm.Symmetric.AES256GCM.encrypt(
                initializationVector = testVector.initializationVector.hexToBytes(),
                encryptionKey = testVector.key.hexToBytes(),
                data = testVector.plainText.hexToBytes()
            ).toHexString()
        }

        private fun decryptAES256GCM(testVector: SymmetricTestVector): String {
            return EncryptionAlgorithm.Symmetric.AES256GCM.decrypt(
                initializationVector = testVector.initializationVector.hexToBytes(),
                encryptionKey = testVector.key.hexToBytes(),
                data = (testVector.cipherText + testVector.tag).hexToBytes()
            ).toHexString()
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

class AsymmetricEncryptionTest {

    /**
     * RSA-2048-OAEP decryption tests
     */

    @Test
    fun `Test RSA decryption`() {
        val keyFactory = KeyFactory.getInstance("RSA")

        val privateKeyModulus = BigInteger("b8e814a25ca64c8de16f73849a78c8b13bb086a407301604f674efb588ee7b996b1b6a2968625a2548e9ab01ce6a3699907e303c8a02c9e40ea36bd6d8b2a74b1ee98fa8835a480dfc751fddc490e5a46707095356316587fc339196e4d7db70c7feae50a1263dedd589bec009624193c7de4793dcdf830be3256c70de1f02f7a7d3503035fcb9625c40abb7445470203902ea045f337d31fcd28506e46cd65560949f08cd90fedaabbcb6615b884737d3f5ad01e67cc0c2997af3328b3c80d5ee0a9aa40a9119bd7594fcfe2324728ea9a8f839e663467a0c44915d0275e34cf1c9605ad317c4573f57c85fd7e19e82cc6f77314e8db47a908a57e3e4418e45", 16)
        val privateKeyExponent = BigInteger("4af58aa7e7776341814a7542247d229ef6dbb1397dd0789cba6cdd60728a7b80ce72e6aeb2aa6c710105f9555a20a4d1cc49dbb42f1ec249b9c5764a3abef222f9fd2547e3380e4ddd327e20a1373c61518300bcd00c6664a251258c4e6953847d0f3a0b65c8e3022fb70fa53a28a2fd0de18692e2cf99889024f3b92dd2d49870a5de6f11827feade31bdc8889148968fad08b794007f68524a3bbce886dee240cb18f0b14e22ebfe5b04a4f1a73c9ed56adc0881b9aca2a02a776a2df2843b3cca528c8dca70db0a72baa978e8e11ef833f298403003de5820cf6d54d58de1753aac48aae6911a55f9d393a829fd4169799365b7a4015c5911277937bb1501", 16)
        val privateKeySpec = RSAPrivateKeySpec(privateKeyModulus, privateKeyExponent)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)

        /*
        val publicKeyExponent = BigInteger("10001", 16)
        val publicKeySpec = RSAPublicKeySpec(privateKeyModulus, publicKeyExponent)
        val publicKey = keyFactory.generatePublic(publicKeySpec)
        */

        val cipherText = "6afdbc76de74458198a9c890cc5abb52580af01c2096036dca104d67f96a05de682da5c26970a808343527440aa80b9d043045d7983f442a3d376e5b039bcfb96c1b5fd0e46b5fff85646273293ced5e7272993850017f24f6133591d5c9788781a9952873ebfc45ad4d34fff2b4e9ababf49d9f9a3d7726bdce3eb2feb545db5cfef0b183bd55735a2d356b4278c5580ce0e4cfd21a0a3ad3b225de388fcfd688394710f97d5a3933e01d434fcff732542390f8915d5d291780ed63d425c0bea5bb0ad25aae3a70355e3f45a443ea111b80515b743d5bd226d339dc7516ce6c41414a0aa978198bc6762f443e957c7be5edbd25fcdd226c5d967fa05d7c9079".hexToBytes()

        val expectedPlainText = "6628194e12073db03ba94cda9ef9532397d50dba79b987004afefe34"

        val plainText = EncryptionAlgorithm.Asymmetric.RSA2048OAEP.decrypt(privateKey.encoded, cipherText)
            .toHexString()

        assertEqualsIgnoringCase(expectedPlainText, plainText)
    }

}