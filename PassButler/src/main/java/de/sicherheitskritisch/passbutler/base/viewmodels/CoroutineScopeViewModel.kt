package de.sicherheitskritisch.passbutler.base.viewmodels

import android.arch.lifecycle.ViewModel
import android.support.annotation.CallSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

open class CoroutineScopeViewModel : ViewModel(), CoroutineScope {

    protected open val coroutineDispatcher = Dispatchers.Default

    override val coroutineContext: CoroutineContext
        get() = coroutineDispatcher + coroutineJob

    /**
     * By default use a `SupervisorJob` to avoid that a failing child job cancel all jobs.
     */
    private val coroutineJob = SupervisorJob()

    // TODO: This is not called on fragment destruction
    @CallSuper
    override fun onCleared() {
        super.onCleared()
        coroutineJob.cancel()
    }
}
