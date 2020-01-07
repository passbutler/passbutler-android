package de.sicherheitskritisch.passbutler.base

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

/**
 * A `MutableLiveData<T>` that only excepts non-null values.
 *
 * Note: The instance must be created on main-thread to set initial value without `postValue` dispatching!
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
 *
 * Note: The instance must be created on main-thread to set initial value without `postValue` dispatching!
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
        // TODO: Do not use `postValue`
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
        // TODO: Do not use `postValue` like all other customizations of LiveData
        postValue(valueGetter())
    }

    fun notifyChange() {
        // TODO: Do not use `postValue`
        val newValue = valueGetter()
        postValue(newValue)
    }
}

@MainThread
fun <T> LiveData<T>.observe(owner: LifecycleOwner, notifyOnRegister: Boolean, observer: Observer<T>) {
    observe(owner, observer)

    if (notifyOnRegister) {
        observer.onChanged(value)
    }
}