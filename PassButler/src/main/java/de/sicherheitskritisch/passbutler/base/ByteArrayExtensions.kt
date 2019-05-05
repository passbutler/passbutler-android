package de.sicherheitskritisch.passbutler.base

fun ByteArray?.optionalContentEquals(other: ByteArray?): Boolean {
    return when {
        this == null && other == null -> true
        this != null && other != null -> this.contentEquals(other)
        else -> false
    }
}

fun ByteArray?.optionalContentNotEquals(other: ByteArray?): Boolean {
    return !optionalContentEquals(other)
}

fun ByteArray?.toHexString(): String {
    return this?.joinToString("") { "%02X".format(it) } ?: ""
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