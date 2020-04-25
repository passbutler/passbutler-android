package de.passbutler.app.base.viewmodels

interface EditableViewModel<EditingViewModelType : EditingViewModel> {
    fun createEditingViewModel(): EditingViewModelType
}

interface EditingViewModel