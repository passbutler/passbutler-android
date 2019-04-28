package de.sicherheitskritisch.passbutler.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CryptoTest {

    // TODO: tests for empty password, salt -> exception

    @Test
    fun `Derive masterkey from password and salt`() {

    }

    // TODO: Add tests for wrong IV, key, no plain etc.

    /**
     * Encryption tests (test vectors took from <https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Algorithm-Validation-Program/documents/mac/gcmtestvectors.zip>)
     */

    @Test
    fun `Encrypt AES-256-GCM test vector 1`() {
        val initializationVector = "8723d729273ae03d5a23c489".hexToBytes()
        val key = "619185cb3fc531e8579fa9c83bc34d4b96adbde82c270895fa06eeaaff116e6b".hexToBytes()
        val plainText = "635ed869c6e842823ff6eda3700a77bf".hexToBytes()

        val expectedCipherText = "eb865af9d3bba92752aa3762eac24f95"
        val expectedTag = "87937463dd4c3526430635ec"

        val encryptionResult = Algorithm.AES256GCM.encrypt(initializationVector, key, plainText).toHexString()
        assertEquals(expectedCipherText + expectedTag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 2`() {
        val initializationVector = "5d91290f8447b4377316b420".hexToBytes()
        val key = "ed1ddbbf5e912cf501888ef59c34df968b1a037ea1995ccbf65449f71f5a8d45".hexToBytes()
        val plainText = "12070ac3b24d9d56be9718d32a43cfab".hexToBytes()

        val expectedCipherText = "b42f843a56825d6870ef38ea214ad7b"
        val expectedTag = "19f931dd3966f09362bab0d0"

        val encryptionResult = Algorithm.AES256GCM.encrypt(initializationVector, key, plainText).toHexString()
        assertEquals(expectedCipherText + expectedTag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 3`() {
        val initializationVector = "36f2002b7f10a23f7d29769e".hexToBytes()
        val key = "3f72bd4142e191ccd23de25fdb06e4e91e04a184f7a3d049563b73602d583f4e".hexToBytes()
        val plainText = "415a9f8adcc806551155cc057e0477e1".hexToBytes()

        val expectedCipherText = "aad9f514040db03b1b8daf504a9d8864"
        val expectedTag = "172aa836412078b9440b1c3a"

        val encryptionResult = Algorithm.AES256GCM.encrypt(initializationVector, key, plainText).toHexString()
        assertEquals(expectedCipherText + expectedTag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 4`() {
        val initializationVector = "dfd8ec63f3b4c890580e45eb".hexToBytes()
        val key = "024a95a9b5217969570ff928de43f4a70cfb38ddd681ec4ccb81a7ce3c3cc509".hexToBytes()
        val plainText = "369ce39a60a68edc5c5f8f23db68723c".hexToBytes()

        val expectedCipherText = "1fb92c1909faf97ed15f4b64ee42ca83"
        val expectedTag = "4787547fe8647a6f30aed275"

        val encryptionResult = Algorithm.AES256GCM.encrypt(initializationVector, key, plainText).toHexString()
        assertEquals(expectedCipherText + expectedTag, encryptionResult)
    }

    @Test
    fun `Encrypt AES-256-GCM test vector 5`() {
        val initializationVector = "a1e1646b6f1bc85bcd0fe970".hexToBytes()
        val key = "41d2fa3c7d6872ca7c3076d4321ea66d0c58b2c17b566b2d703ddfbb5aa62197".hexToBytes()
        val plainText = "9502d94aa906768381a1bc474012a7b7".hexToBytes()

        val expectedCipherText = "059af05971b0563af33a1eb3aa8182d4"
        val expectedTag = "4aa417e03c5814410d6cd5a6"

        val encryptionResult = Algorithm.AES256GCM.encrypt(initializationVector, key, plainText).toHexString()
        assertEquals(expectedCipherText + expectedTag, encryptionResult)
    }
}

private fun String.hexToBytes(): ByteArray {
    return ByteArray(this.length / 2) {
        this.substring(it * 2, (it * 2) + 2).toInt(16).toByte()
    }
}

private fun ByteArray?.toHexString(): String {
    return this?.joinToString("") { "%02x".format(it) } ?: ""
}