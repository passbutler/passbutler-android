package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider

class AppViewModelFactory(private val rootViewModel: RootViewModel) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            LoginViewModel::class.java.isAssignableFrom(modelClass) -> {
                modelClass.getConstructor(RootViewModel::class.java).newInstance(rootViewModel)
            }
            OverviewViewModel::class.java.isAssignableFrom(modelClass) -> {
                modelClass.getConstructor(RootViewModel::class.java).newInstance(rootViewModel)
            }
            else -> super.create(modelClass)
        }
    }
}
