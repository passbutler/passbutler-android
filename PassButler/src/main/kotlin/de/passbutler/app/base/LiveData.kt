package de.passbutler.app.base

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

@MainThread
fun <T> LiveData<T>.observe(owner: LifecycleOwner, notifyOnRegister: Boolean, observer: Observer<T>) {
    observe(owner, observer)

    if (notifyOnRegister) {
        observer.onChanged(value)
    }
}

/**
 * A `MutableLiveData<T>` that only excepts non-null values.
 */
class NonNullMutableLiveData<T : Any>(initialValue: T) : MutableLiveData<T>(initialValue) {
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
 * A `LiveData<T>` that retrieves its value via lambda.
 * On a known change of values used in the lambda, `notifyChange()` must be called on main-thread!
 */
open class ValueGetterLiveData<T>(private val valueGetter: () -> T) : LiveData<T>(valueGetter()) {
    @MainThread
    fun notifyChange() {
        value = valueGetter()
    }
}

/**
 * A non-null value enforcing `ValueGetterLiveData`.
 */
class NonNullValueGetterLiveData<T : Any>(valueGetter: () -> T) : ValueGetterLiveData<T>(valueGetter) {
    override fun getValue(): T {
        // Because non-null type is enforced by Kotlin the double-bang is okay
        return super.getValue()!!
    }
}

/**
 * An optional value behaving `ValueGetterLiveData`.
 */
class OptionalValueGetterLiveData<T : Any?>(valueGetter: () -> T) : ValueGetterLiveData<T>(valueGetter)

/**
 * An optional value behaving `ValueGetterLiveData` that uses dependent `LiveData` to trigger change.
 */
class DependentOptionalValueGetterLiveData<T : Any?>(private vararg val dependencies: LiveData<out Any?>, valueGetter: () -> T) : ValueGetterLiveData<T>(valueGetter) {
    private val dependenciesChangedObserver = Observer<Any?> {
        notifyChange()
    }

    override fun onActive() {
        super.onActive()

        dependencies.forEach {
            it.observeForever(dependenciesChangedObserver)
        }
    }

    override fun onInactive() {
        super.onInactive()

        dependencies.forEach {
            it.removeObserver(dependenciesChangedObserver)
        }
    }
}

@MainThread
fun <T> ValueGetterLiveData<T>.observe(owner: LifecycleOwner, notifyOnRegister: Boolean, observer: Observer<T>) {
    observe(owner, observer)

    if (notifyOnRegister) {
        notifyChange()
        observer.onChanged(value)
    }
}