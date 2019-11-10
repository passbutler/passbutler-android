package de.sicherheitskritisch.passbutler.base.viewmodels

interface EditableViewModel<EditingViewModelType : EditingViewModel> {
    fun createEditingViewModel(): EditingViewModelType
}

interface EditingViewModel