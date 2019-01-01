package de.sicherheitskritisch.passbutler.ui

import android.arch.lifecycle.ViewModel

open class BaseViewModelFragment<ViewModelType: ViewModel> : BaseFragment() {
    lateinit var viewModel: ViewModelType
}