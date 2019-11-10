package de.sicherheitskritisch.passbutler.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * A `MutableLiveData<T>` that only excepts non-null values.
 */
class NonNullMutableLiveData<T : Any>(initialValue: T) : MutableLiveData<T>() {
    init {
        value = initialValue
    }

    override fun getValue(): T {
        // Because non-null type is enforced by Kotlin the double-bang is okay
        return super.getValue()!!
    }

    // It is not redundant because it ensures non-nullability
    @Suppress("RedundantOverride")
    override fun setValue(value: T) {
        super.setValue(value)
    }

    // It is not redundant because it ensures non-nullability
    @Suppress("RedundantOverride")
    override fun postValue(value: T) {
        super.postValue(value)
    }
}

/**
 * A non-null value enforcing `LiveData<T>` that retrieves its value via lambda.
 * On a known change of values used in the lambda, `notifyChange()` must be called!
 */
class NonNullValueGetterLiveData<T : Any>(private val valueGetter: () -> T) : LiveData<T>() {
    init {
        value = valueGetter()
    }

    override fun getValue(): T {
        // Because non-null type is enforced by Kotlin the double-bang is okay
        return super.getValue()!!
    }

    fun notifyChange() {
        val newValue = valueGetter()
        postValue(newValue)
    }
}

/**
 * A default `LiveData<T>` that retrieves its value via lambda.
 * On a known change of values used in the lambda, `notifyChange()` must be called!
 */
class ValueGetterLiveData<T : Any?>(private val valueGetter: () -> T) : LiveData<T>() {
    init {
        value = valueGetter()
    }

    fun notifyChange() {
        val newValue = valueGetter()
        postValue(newValue)
    }
}