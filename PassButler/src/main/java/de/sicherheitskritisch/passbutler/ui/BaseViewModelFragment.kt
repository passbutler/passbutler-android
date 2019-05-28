package de.sicherheitskritisch.passbutler.ui

import androidx.lifecycle.ViewModel

open class BaseViewModelFragment<ViewModelType: ViewModel> : BaseFragment() {
    lateinit var viewModel: ViewModelType
}