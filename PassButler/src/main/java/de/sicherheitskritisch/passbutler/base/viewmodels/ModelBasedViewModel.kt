package de.sicherheitskritisch.passbutler.base.viewmodels

interface ModelBasedViewModel<Model> {
    fun createModel(): Model
}