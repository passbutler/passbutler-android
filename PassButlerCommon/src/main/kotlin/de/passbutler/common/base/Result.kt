package de.passbutler.common.base

sealed class Result<out T>

class Success<out T>(val result: T): Result<T>()
class Failure(val throwable: Throwable): Result<Nothing>()

fun <T> Result<T>.resultOrThrowException(): T {
    return when(this) {
        is Success -> this.result
        is Failure -> throw throwable
    }
}