package de.sicherheitskritisch.passbutler.base.viewmodels

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.support.annotation.CallSuper
import de.sicherheitskritisch.passbutler.base.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

open class CoroutineScopeAndroidViewModel(application: Application) : AndroidViewModel(application), CoroutineScope {

    /**
     * By default use the `IO` dispatcher for time-intensive tasks and not the `Default` dispatcher for CPU-intensive tasks.
     */
    protected open val coroutineDispatcher = Dispatchers.IO

    override val coroutineContext: CoroutineContext
        get() = coroutineDispatcher + coroutineJob

    /**
     * By default use a `SupervisorJob` to avoid that a failing child job cancel all jobs.
     */
    private val coroutineJob = SupervisorJob()

    @CallSuper
    override fun onCleared() {
        L.d("CoroutineScopeAndroidViewModel", "onCleared(): ${this.javaClass.simpleName} - Cancel the coroutine job...")
        coroutineJob.cancel()

        super.onCleared()
    }
}
