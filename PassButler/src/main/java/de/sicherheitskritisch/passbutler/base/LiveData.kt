package de.sicherheitskritisch.passbutler.base

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.support.annotation.MainThread

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

