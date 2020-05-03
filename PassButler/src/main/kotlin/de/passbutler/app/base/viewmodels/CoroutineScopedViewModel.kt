package de.passbutler.app.base.viewmodels

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.tinylog.kotlin.Logger
import kotlin.coroutines.CoroutineContext

open class CoroutineScopedViewModel : ViewModel(), CoroutineScope {

    /**
     * By default use the `IO` dispatcher for time-intensive tasks and not the `Default` dispatcher for CPU-intensive tasks.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    /**
     * By default use a `SupervisorJob` to avoid that a failing child job cancel all jobs.
     */
    private val coroutineJob = SupervisorJob()

    @CallSuper
    override fun onCleared() {
        Logger.debug("${javaClass.simpleName}: Cancel the coroutine job...")
        coroutineJob.cancel()
        super.onCleared()
    }
}
