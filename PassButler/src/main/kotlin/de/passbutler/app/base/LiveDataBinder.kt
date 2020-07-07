package de.passbutler.app.base

import android.view.View
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.material.textfield.TextInputEditText
import de.passbutler.app.ui.visible

/**
 * Visibility binders
 */

fun <T> View.bindVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<T>, block: (T) -> Boolean) {
    liveDataDependency.observe(lifecycleOwner, true, Observer { newValue ->
        visible = block(newValue)
    })
}

fun View.bindVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<Boolean>) {
    liveDataDependency.observe(lifecycleOwner, true, Observer { newValue ->
        visible = newValue
    })
}

fun View.bindVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency1: LiveData<Boolean>, liveDataDependency2: LiveData<Boolean>, block: (Boolean, Boolean) -> Boolean) {
    liveDataDependency1.observe(lifecycleOwner, false, Observer { newValue ->
        visible = block(newValue, liveDataDependency2.value ?: false)
    })

    liveDataDependency2.observe(lifecycleOwner, true, Observer { newValue ->
        visible = block(liveDataDependency1.value ?: false, newValue)
    })
}

fun View.bindVisibility(
    lifecycleOwner: LifecycleOwner,
    liveDataDependency1: LiveData<Boolean>,
    liveDataDependency2: LiveData<Boolean>,
    liveDataDependency3: LiveData<Boolean>,
    block: (Boolean, Boolean, Boolean) -> Boolean
) {
    liveDataDependency1.observe(lifecycleOwner, false, Observer { newValue ->
        visible = block(newValue, liveDataDependency2.value ?: false, liveDataDependency3.value ?: false)
    })

    liveDataDependency2.observe(lifecycleOwner, true, Observer { newValue ->
        visible = block(liveDataDependency1.value ?: false, newValue, liveDataDependency3.value ?: false)
    })

    liveDataDependency3.observe(lifecycleOwner, true, Observer { newValue ->
        visible = block(liveDataDependency1.value ?: false, liveDataDependency2.value ?: false, newValue)
    })
}

/**
 * Enabled binders
 */

fun View.bindEnabled(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<Boolean>) {
    liveDataDependency.observe(lifecycleOwner, true, Observer { newValue ->
        isEnabled = newValue
    })
}

/**
 * Text binders
 */

fun TextView.bindText(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<String>) {
    liveDataDependency.observe(lifecycleOwner, true, Observer { newValue ->
        text = newValue
    })
}

fun TextView.bindTextAndVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<String?>) {
    liveDataDependency.observe(lifecycleOwner, true, Observer { newValue ->
        if (newValue != null) {
            text = newValue
            visible = true
        } else {
            text = ""
            visible = false
        }
    })
}

fun <T> TextView.bindTextAndVisibility(lifecycleOwner: LifecycleOwner, liveDataDependency: LiveData<T?>, transform: (T?) -> String?) {
    liveDataDependency.observe(lifecycleOwner, true, Observer { newValue ->
        val newTransformedValue = transform(newValue)

        if (newTransformedValue != null) {
            text = newTransformedValue
            visible = true
        } else {
            text = ""
            visible = false
        }
    })
}

/**
 * Input binders
 */

fun TextInputEditText.bindInput(liveData: MutableLiveData<String>) {
    setText(liveData.value ?: "")
    addTextChangedListener { newText ->
        liveData.value = newText.toString()
    }
}