package de.sicherheitskritisch.passbutler.base

import de.sicherheitskritisch.passbutler.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger

fun BaseFragment.launchRequestSending(
    handleSuccess: (() -> Unit)? = null,
    handleFailure: ((error: Throwable) -> Unit)? = null,
    handleLoadingChanged: ((isLoading: Boolean) -> Unit)? = blockingProgressScreen(),
    block: suspend () -> Result<*>
): Job {
    val fragmentClassName = javaClass.simpleName

    return launch {
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