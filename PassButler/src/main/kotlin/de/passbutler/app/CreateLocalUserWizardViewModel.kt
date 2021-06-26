package de.passbutler.app

import androidx.lifecycle.ViewModel
import de.passbutler.common.base.MutableBindable

class CreateLocalUserWizardViewModel : ViewModel() {
    val username = MutableBindable<String?>(null)
    val masterPassword = MutableBindable<String?>(null)
}
