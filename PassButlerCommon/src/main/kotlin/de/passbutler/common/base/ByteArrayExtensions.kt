package de.passbutler.common.base

import java.util.*

fun ByteArray?.toHexString(): String {
    return this?.joinToString("") { "%02x".format(it) } ?: ""
}

/**
 * Converts the `ByteArray` to `String` with UTF-8 charset (basically what the `String()` constructor does but in explicit way).
 */
fun ByteArray.toUTF8String(): String {
    return toString(Charsets.UTF_8)
}

/**
 * Extensions to convert `ByteArray` to Base64 string and vice versa.
 */

fun ByteArray.toBase64String(): String {
    return Base64.getEncoder().encodeToString(this)
}

@Throws(IllegalArgumentException::class)
fun String.toByteArrayFromBase64String(): ByteArray {
    return Base64.getDecoder().decode(this)
}

/**
 * Clears out a `ByteArray` for security reasons (for crypto keys etc.).
 */
fun ByteArray.clear() {
    this.forEachIndexed { index, _ ->
        this[index] = 0
    }
}

val ByteArray.bitSize: Int
    get() = size * 8

val Int.byteSize: Int
    get() = this / 8