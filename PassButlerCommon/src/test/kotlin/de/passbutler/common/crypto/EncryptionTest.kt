package de.passbutler.common.crypto

import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.base.toHexString
import de.passbutler.common.hexToBytes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec

class EncryptionTest {

    @Nested
    inner class Symmetric {

        /**
         * AES-256-GCM encryption tests
         */

        @Test
        fun `Encrypt with an empty initialization vector throws an exception`() {
            val testVector = invalidTestVectors.getValue("tooLongInitializationVector")

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { encryptAES256GCM(testVector) }
            }
            assertEquals("The initialization vector must be 96 bits long!", exception.message)
        }

        @Test
        fun `Encrypt with a too long initialization vector throws an exception`() {
            val testVector = invalidTestVectors.getValue("emptyInitializationVector")

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { encryptAES256GCM(testVector) }
            }
            assertEquals("The initialization vector must be 96 bits long!", exception.message)
        }

        @Test
        fun `Encrypt with an empty key throws an exception`() {
            val testVector = invalidTestVectors.getValue("emptyKey")

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { encryptAES256GCM(testVector) }
            }
            assertEquals("The encryption key must be 256 bits long!", exception.message)
        }

        @Test
        fun `Encrypt with a too long key throws an exception`() {
            val testVector = invalidTestVectors.getValue("tooLongKey")

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { encryptAES256GCM(testVector) }
            }
            assertEquals("The encryption key must be 256 bits long!", exception.message)
        }

        @Test
        fun `Encrypt AES-256-GCM valid test vectors`() {
            validTestVectors.forEach { testVector ->
                val encryptionResult = encryptAES256GCM(testVector).resultOrThrowException()
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

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { decryptAES256GCM(testVector) }
            }
            assertEquals("The initialization vector must be 96 bits long!", exception.message)
        }

        @Test
        fun `Decrypt with a too long initialization vector throws an exception`() {
            val testVector = invalidTestVectors.getValue("emptyInitializationVector")

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { decryptAES256GCM(testVector) }
            }
            assertEquals("The initialization vector must be 96 bits long!", exception.message)
        }

        @Test
        fun `Decrypt with an empty key throws an exception`() {
            val testVector = invalidTestVectors.getValue("emptyKey")

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { decryptAES256GCM(testVector) }
            }
            assertEquals("The encryption key must be 256 bits long!", exception.message)
        }

        @Test
        fun `Decrypt with a too long key throws an exception`() {
            val testVector = invalidTestVectors.getValue("tooLongKey")

            val exception = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { decryptAES256GCM(testVector) }
            }
            assertEquals("The encryption key must be 256 bits long!", exception.message)
        }

        @Test
        fun `Decrypt AES-256-GCM valid test vectors`() {
            validTestVectors.forEach { testVector ->
                val decryptionResult = decryptAES256GCM(testVector).resultOrThrowException()
                assertEquals(testVector.plainText, decryptionResult)
            }

            assertEquals(8, validTestVectors.size)
        }

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

        private fun encryptAES256GCM(testVector: SymmetricTestVector): Result<String> {
            val result = runBlocking {
                EncryptionAlgorithm.Symmetric.AES256GCM.encrypt(
                    initializationVector = testVector.initializationVector.hexToBytes(),
                    encryptionKey = testVector.key.hexToBytes(),
                    data = testVector.plainText.hexToBytes()
                )
            }
            return when (result) {
                is Success -> Success(result.result.toHexString())
                is Failure -> Failure(result.throwable)
            }
        }

        private fun decryptAES256GCM(testVector: SymmetricTestVector): Result<String> {
            val result = runBlocking {
                EncryptionAlgorithm.Symmetric.AES256GCM.decrypt(
                    initializationVector = testVector.initializationVector.hexToBytes(),
                    encryptionKey = testVector.key.hexToBytes(),
                    data = (testVector.cipherText + testVector.tag).hexToBytes()
                )
            }
            return when (result) {
                is Success -> Success(result.result.toHexString())
                is Failure -> Failure(result.throwable)
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

    @Nested
    inner class Asymmetric {

        /**
         * RSA-2048-OAEP decryption tests
         */

        @Test
        fun `Decrypt RSA-2048-OAEP test 1`() {
            val testVectorWithData = AsymmetricTestVectorWithData(
                modulus = "b8e814a25ca64c8de16f73849a78c8b13bb086a407301604f674efb588ee7b996b1b6a2968625a2548e9ab01ce6a3699907e303c8a02c9e40ea36bd6d8b2a74b1ee98fa8835a480dfc751fddc490e5a46707095356316587fc339196e4d7db70c7feae50a1263dedd589bec009624193c7de4793dcdf830be3256c70de1f02f7a7d3503035fcb9625c40abb7445470203902ea045f337d31fcd28506e46cd65560949f08cd90fedaabbcb6615b884737d3f5ad01e67cc0c2997af3328b3c80d5ee0a9aa40a9119bd7594fcfe2324728ea9a8f839e663467a0c44915d0275e34cf1c9605ad317c4573f57c85fd7e19e82cc6f77314e8db47a908a57e3e4418e45",
                publicKeyExponent = "10001",
                secretKeyExponent = "4af58aa7e7776341814a7542247d229ef6dbb1397dd0789cba6cdd60728a7b80ce72e6aeb2aa6c710105f9555a20a4d1cc49dbb42f1ec249b9c5764a3abef222f9fd2547e3380e4ddd327e20a1373c61518300bcd00c6664a251258c4e6953847d0f3a0b65c8e3022fb70fa53a28a2fd0de18692e2cf99889024f3b92dd2d49870a5de6f11827feade31bdc8889148968fad08b794007f68524a3bbce886dee240cb18f0b14e22ebfe5b04a4f1a73c9ed56adc0881b9aca2a02a776a2df2843b3cca528c8dca70db0a72baa978e8e11ef833f298403003de5820cf6d54d58de1753aac48aae6911a55f9d393a829fd4169799365b7a4015c5911277937bb1501",
                plainText = "6628194e12073db03ba94cda9ef9532397d50dba79b987004afefe34",
                cipherText = "6afdbc76de74458198a9c890cc5abb52580af01c2096036dca104d67f96a05de682da5c26970a808343527440aa80b9d043045d7983f442a3d376e5b039bcfb96c1b5fd0e46b5fff85646273293ced5e7272993850017f24f6133591d5c9788781a9952873ebfc45ad4d34fff2b4e9ababf49d9f9a3d7726bdce3eb2feb545db5cfef0b183bd55735a2d356b4278c5580ce0e4cfd21a0a3ad3b225de388fcfd688394710f97d5a3933e01d434fcff732542390f8915d5d291780ed63d425c0bea5bb0ad25aae3a70355e3f45a443ea111b80515b743d5bd226d339dc7516ce6c41414a0aa978198bc6762f443e957c7be5edbd25fcdd226c5d967fa05d7c9079"
            )

            val secretKeyBytes = createSecretKeyBytes(testVectorWithData)
            val plainText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.decrypt(secretKeyBytes, testVectorWithData.cipherText.hexToBytes()).resultOrThrowException() }
            assertArrayEquals(testVectorWithData.plainText.hexToBytes(), plainText)
        }

        @Test
        fun `Decrypt RSA-2048-OAEP test 2`() {
            val testVectorWithData = AsymmetricTestVectorWithData(
                modulus = "cde3ab0375e3847f0b2c459eb76eb9d6824d428c54af538d942115ce32c7ca3829be5ed97b915aad5f376c42df72b3a505740e2f34e67fe6c980c6279d9ad9532ff2514734031468b3550e89fc2b557c0340bb361cda5a815ae786b3c918f70979078caefe650a6200c9a46ce59dd75d6f4b6b869b73d3fb0905e53f8de73111328e613210820e73390b4b6552ea4d6b2cfc4a6bbfac19cd8049f0d278e7af83f16e6949ee0d0b3d241e3d92753f2ff79c0e2bd7b94844677dc7f2643e00f144d28dc7433be72e5105759d44424a67a440bb25419eb4683f65e8f5ed6d29443a0cce635cb1b8e6feb7b02a763e370b6bccc53b51decaeb203af1e33d661ac149",
                publicKeyExponent = "10001",
                secretKeyExponent = "84613021e778c446534ec1eccd107a98bbeb2530c97a79847c8bd153653f247c7c0a953dccbfa6c7f682d22f2530c0e507de99082d414f577a943bd458f7a9685d59b67ccbaa7742e29b7bdfa8adcc2712885a56eeb24ea3016a002834d2c273eac7b9e3025ac114466160414c59f29176efcd511d3e9a3bbc8f77d9274a5137100fa2eb1a63c3ccb002e007320f76fde5c32c126cf8d7d0be5ca53725462b750e47c2578af34b9db77102afb52da043aa8268def42fee835c0d11ccfd9fd1db05b9090e9509f13b8a266d97846b810ac7b0847ddfe3c1f9bfd66069fece44dca45cabc832a95638f54d07e3e6eb501c576ce98fa7de909220709f81bf65b569",
                plainText = "8ff00caa605c702830634d9a6c3d42c652b58cf1d92fec570beee7",
                cipherText = "cc4965483f93e3bee5a7affa3aa10f890cc2acd94039b713b46ee7e089b1a0ef245fd92d5b3049989de505d8cdf24f2da373aca71564bd6b5e50e34922ac4e7b08722c0efa53e705071beaa51015fa640a57f92ba19971ff195cf23843609eb85dc199504b76df1c97bf4c8e8941074ffed687d40f98c441da6332ccf8264faaf844e288df72ea2cc754eb2827087c1c91574628f91533e7234eb7a6e8e665ec326668e5089833aa8ab7ddd54489b3a00a522fa19bb72335dcf7890d6e5ee046ce6e9d279baf0adb678511e7c3fb9ed59576a5c261f57a4b6b29bd68ffefc5158d0cbfc8d2d68a1b03f2ee0cf7f9b05c9be7f241650f13ea17314ea9854ad9aa"
            )

            val secretKeyBytes = createSecretKeyBytes(testVectorWithData)
            val plainText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.decrypt(secretKeyBytes, testVectorWithData.cipherText.hexToBytes()).resultOrThrowException() }
            assertArrayEquals(testVectorWithData.plainText.hexToBytes(), plainText)
        }

        /**
         * RSA-2048-OAEP encryption and decryption tests
         */

        @Test
        fun `Encrypt and decrypt with RSA-2048-OAEP test 1 (plain text length 0)`() {
            val plainText = "".hexToBytes()

            val publicKeyBytes = createPublicKeyBytes(encryptDecryptTestVector)
            val encryptedPlainText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.encrypt(publicKeyBytes, plainText).resultOrThrowException() }

            val secretKeyBytes = createSecretKeyBytes(encryptDecryptTestVector)
            val decryptedCipherText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.decrypt(secretKeyBytes, encryptedPlainText).resultOrThrowException() }

            assertArrayEquals(plainText, decryptedCipherText)
        }

        @Test
        fun `Encrypt and decrypt with RSA-2048-OAEP test 2 (plain text length 16)`() {
            val plainText = "087820b569e8fa8d".hexToBytes()

            val publicKeyBytes = createPublicKeyBytes(encryptDecryptTestVector)
            val encryptedPlainText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.encrypt(publicKeyBytes, plainText).resultOrThrowException() }

            val secretKeyBytes = createSecretKeyBytes(encryptDecryptTestVector)
            val decryptedCipherText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.decrypt(secretKeyBytes, encryptedPlainText).resultOrThrowException() }

            assertArrayEquals(plainText, decryptedCipherText)
        }

        @Test
        fun `Encrypt and decrypt with RSA-2048-OAEP test 3 (plain text length 56)`() {
            val plainText = "4653acaf171960b01f52a7be63a3ab21dc368ec43b50d82ec3781e04".hexToBytes()

            val publicKeyBytes = createPublicKeyBytes(encryptDecryptTestVector)
            val encryptedPlainText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.encrypt(publicKeyBytes, plainText).resultOrThrowException() }

            val secretKeyBytes = createSecretKeyBytes(encryptDecryptTestVector)
            val decryptedCipherText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.decrypt(secretKeyBytes, encryptedPlainText).resultOrThrowException() }

            assertArrayEquals(plainText, decryptedCipherText)
        }

        @Test
        fun `Encrypt and decrypt with RSA-2048-OAEP test 4 (plain text length 112)`() {
            val plainText = "3c3bad893c544a6d520ab022319188c8d504b7a788b850903b85972eaa18552e1134a7ad6098826254ff7ab672b3d8eb3158fac6d4cbaef1".hexToBytes()

            val publicKeyBytes = createPublicKeyBytes(encryptDecryptTestVector)
            val encryptedPlainText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.encrypt(publicKeyBytes, plainText).resultOrThrowException() }

            val secretKeyBytes = createSecretKeyBytes(encryptDecryptTestVector)
            val decryptedCipherText = runBlocking { EncryptionAlgorithm.Asymmetric.RSA2048OAEP.decrypt(secretKeyBytes, encryptedPlainText).resultOrThrowException() }

            assertArrayEquals(plainText, decryptedCipherText)
        }

        /**
         * Test vectors took from: <https://github.com/pyca/cryptography/blob/2.4.x/vectors/cryptography_vectors/asymmetric/RSA/oaep-custom/oaep-sha256-sha256.txt>
         */
        private val encryptDecryptTestVector = AsymmetricTestVector(
            modulus = "a80bcb66c3a263f54277a1acb007c66e64d57b963c095650cbf9f50148a62b17ef14a7cddb1d7d8fbf3c0234c75547f85c2b9e79b4c2680e1806f402148056603422ff5a3a3e10967fb1f17f7c02cc9ba61c52c67180a5a81dcd4db94e99559eb321041340d39ae2f7667808a33437d2f04c88635eb7d07aec145949eddfb12e910ba47cc973f9dbd565259f1007bb82ff4ca8c47abdbea7fe79ca31770ffa0e84683429aa52b628736ba280448c7b39f74855fd44fc549aa08f20f9e47cd68e35233425dfef61a65dab685fdf86032c7d99f6285de42b7a6803eb7e82eb42eac63aa7b136e1485f5ba0fc66942423fc33d2b226b0eeadd0edc76c908fe63061",
            publicKeyExponent = "10001",
            secretKeyExponent = "68fc75670e7235e0d455c93c09fdd18ac6945951d2d0428cd7e2a19edbb474d7cf1628800394b90d457c48249124468273930cbf1c9f184335dfa2326a7c837a3718665008731e09e85d53734216bd9dc079917d490c0672b1abc2133377b8761d9352e87467bf2c6d442759be1cb183a77d28f86c048acb4112b575ea970629fdb520e547316c1f6fa963daee38102a3046c2f0676edf368aab1d089328be7468a3b3b317ffbc17f8b6fb5e5893b5d5b8b54b697424181514432406e0725036837b3f07a8d1c9d23e4a17a0254c4bdb987ab5ba00f190d4f72402c31165c5c8dd01f83b384d826acf991e4caa4751be40db0d19e471cc05f86920e6403b5ed"
        )

        private fun createPublicKeyBytes(testVector: AsymmetricTestVector): ByteArray {
            val keyFactory = KeyFactory.getInstance("RSA")

            val publicKeyModulus = BigInteger(testVector.modulus, 16)
            val publicKeyExponent = BigInteger(testVector.publicKeyExponent, 16)

            val publicKeySpec = RSAPublicKeySpec(publicKeyModulus, publicKeyExponent)
            return keyFactory.generatePublic(publicKeySpec).encoded
        }

        private fun createSecretKeyBytes(testVector: AsymmetricTestVector): ByteArray {
            val keyFactory = KeyFactory.getInstance("RSA")

            val secretKeyModulus = BigInteger(testVector.modulus, 16)
            val secretKeyExponent = BigInteger(testVector.secretKeyExponent, 16)

            val secretKeySpec = RSAPrivateKeySpec(secretKeyModulus, secretKeyExponent)
            return keyFactory.generatePrivate(secretKeySpec).encoded
        }
    }

    private class AsymmetricTestVectorWithData(
        modulus: String,
        publicKeyExponent: String,
        secretKeyExponent: String,
        val plainText: String,
        val cipherText: String
    ) : AsymmetricTestVector(modulus, publicKeyExponent, secretKeyExponent)

    private open class AsymmetricTestVector(
        val modulus: String,
        val publicKeyExponent: String,
        val secretKeyExponent: String
    )
}