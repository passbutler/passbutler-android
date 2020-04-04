package de.sicherheitskritisch.passbutler.base

import de.sicherheitskritisch.passbutler.ui.BaseFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import kotlin.coroutines.EmptyCoroutineContext

fun BaseFragment.launchRequestSending(
    handleSuccess: (() -> Unit)? = null,
    handleFailure: ((error: Throwable) -> Unit)? = null,
    handleLoadingChanged: ((isLoading: Boolean) -> Unit)? = blockingProgressScreen(),
    isCancellable: Boolean = true,
    block: suspend () -> Result<*>
): Job {
    val fragmentClassName = javaClass.simpleName

    val coroutineContext = if (isCancellable) {
        EmptyCoroutineContext
    } else {
        NonCancellable
    }

    return launch(coroutineContext) {
        try {
            handleLoadingChanged?.invoke(true)

            val result = withContext(Dispatchers.IO) {
                block()
            }

            handleLoadingChanged?.invoke(false)

            when (result) {
                is Success -> handleSuccess?.invoke()
                is Failure -> {
                    val exception = result.throwable
                    Logger.warn(exception, "${fragmentClassName}: The operation failed with exception")
                    handleFailure?.invoke(exception)
                }
            }
        } catch (cancellationException: CancellationException) {
            Logger.warn(cancellationException, "The job was cancelled!")
        }
    }
}

private fun BaseFragment.blockingProgressScreen(): (Boolean) -> Unit {
    return { isLoading ->
        if (isLoading) {
            showProgress()
        } else {
            hideProgress()
        }
    }
}