package de.sicherheitskritisch.passbutler.base.viewmodels

import android.app.Application
import androidx.annotation.CallSuper
import androidx.lifecycle.AndroidViewModel
import de.sicherheitskritisch.passbutler.base.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

open class CoroutineScopeAndroidViewModel(application: Application) : AndroidViewModel(application), CoroutineScope {

    /**
     * By default use the `IO` dispatcher for time-intensive tasks and not the `Default` dispatcher for CPU-intensive tasks.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    /**
     * By default use a `SupervisorJob` to avoid that a failing child job cancel all jobs.
     */
    protected val coroutineJob = SupervisorJob()

    @CallSuper
    override fun onCleared() {
        L.d(javaClass.simpleName, "onCleared(): Cancel the coroutine job...")
        coroutineJob.cancel()
        super.onCleared()
    }
}
