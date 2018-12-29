package de.sicherheitskritisch.pass.ui

import android.arch.lifecycle.ViewModel

open class BaseViewModelFragment<ViewModelType: ViewModel> : BaseFragment() {
    var viewModel: ViewModelType? = null
}