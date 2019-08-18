package de.sicherheitskritisch.passbutler.base

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

open class NonNullMutableLiveData<T : Any>(initialValue: T) : MutableLiveData<T>() {
    init {
        value = initialValue
    }

    override fun getValue(): T {
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

class ValueGetterLiveData<T : Any>(private val valueGetter: () -> T) : NonNullMutableLiveData<T>(initialValue = valueGetter()) {
    fun notifyChange() {
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

@MainThread
fun <T> LiveData<T>.observeForever(notifyOnRegister: Boolean, observer: Observer<T>) {
    observeForever(observer)

    if (notifyOnRegister) {
        observer.onChanged(value)
    }
}

@MainThread
fun <T> LiveData<T>.observeOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    observe(lifecycleOwner, object : Observer<T> {
        override fun onChanged(t: T?) {
            observer.onChanged(t)
            removeObserver(this)
        }
    })
}

@MainThread
fun <T> LiveData<T>.observeForeverNotifyForNonNullValues(observer: (T) -> Unit) {
    observeForever {
        if (it != null) {
            observer(it)
        }
    }
}