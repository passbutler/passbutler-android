package de.sicherheitskritisch.passbutler.base.viewmodels

import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

open class ManualCancelledCoroutineScopeViewModel : ViewModel(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = SupervisorJob()

    fun cancelJobs() {
        L.d(javaClass.simpleName, "cancelJobs(): Cancel the coroutine job...")
        coroutineJob.cancel()
    }
}
