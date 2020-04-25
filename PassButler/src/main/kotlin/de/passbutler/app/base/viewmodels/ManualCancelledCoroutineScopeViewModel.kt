package de.passbutler.app.base.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.tinylog.kotlin.Logger
import kotlin.coroutines.CoroutineContext

open class ManualCancelledCoroutineScopeViewModel : ViewModel(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = SupervisorJob()

    fun cancelJobs() {
        Logger.debug("${javaClass.simpleName}: Cancel the coroutine job...")
        coroutineJob.cancel()
    }
}
