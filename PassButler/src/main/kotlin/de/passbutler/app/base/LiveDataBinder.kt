package de.passbutler.app.base

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.passbutler.app.ui.visible

fun <T> View.bindVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<T>?, block: (T) -> Boolean) {
    liveDataDependency?.observe(lifecycleOwner, true, Observer { newValue ->
        visible = block(newValue)
    })
}

fun View.bindVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<Boolean>?) {
    liveDataDependency?.observe(lifecycleOwner, true, Observer { newValue ->
        visible = newValue
    })
}

fun View.bindVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency1: LiveData<Boolean>?, liveDataDependency2: LiveData<Boolean>?) {
    liveDataDependency1?.observe(lifecycleOwner, false, Observer { newValue ->
        visible = newValue && liveDataDependency2?.value ?: false
    })

    liveDataDependency2?.observe(lifecycleOwner, true, Observer { newValue ->
        visible = newValue && liveDataDependency1?.value ?: false
    })
}

fun View.bindEnabled(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<Boolean>?) {
    liveDataDependency?.observe(lifecycleOwner, true, Observer { newValue ->
        isEnabled = newValue
    })
}