package de.sicherheitskritisch.passbutler.common

abstract class AsyncCallback<ResultType, ErrorType> {
    abstract fun onSuccess(result: ResultType? = null)
    abstract fun onFailure(error: ErrorType)
}

sealed class AsyncCallbackResult<ResultType, ErrorType> {
    class Success<ResultType, ErrorType>(val result: ResultType?) : AsyncCallbackResult<ResultType, ErrorType>()
    class Failure<ResultType, ErrorType>(val error: ErrorType) : AsyncCallbackResult<ResultType, ErrorType>()
}

fun <ResultType, ErrorType> asyncCallback(
    resultHandler: (AsyncCallbackResult<ResultType, ErrorType>) -> Unit
): AsyncCallback<ResultType, ErrorType> {
    return object : AsyncCallback<ResultType, ErrorType>() {
        override fun onSuccess(result: ResultType?) {
            resultHandler(AsyncCallbackResult.Success(result))
        }

        override fun onFailure(error: ErrorType) {
            resultHandler(AsyncCallbackResult.Failure(error))
        }
    }
}

