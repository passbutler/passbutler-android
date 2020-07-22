package de.passbutler.app.base

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.passbutler.common.base.Bindable
import de.passbutler.common.base.BindableObserver

fun <T> Bindable<T>.addLifecycleObserver(lifecycleOwner: LifecycleOwner, notifyOnRegister: Boolean, observer: BindableObserver<T>) {
    addObserver(lifecycleOwner.lifecycleScope, notifyOnRegister, observer)

    lifecycleOwner.lifecycle.addObserver(DestroyedStateLifecycleEventObserver {
        // Unregister observer of `Bindable`
        removeObserver(observer)
    })
}

class DestroyedStateLifecycleEventObserver(private val destroyedStateCallback: () -> Unit) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (source.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            // Unregister lifecycle observer itself
            source.lifecycle.removeObserver(this)

            destroyedStateCallback()
        }
    }
}