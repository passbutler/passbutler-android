package de.passbutler.app.ui

import android.view.View
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.textfield.TextInputEditText
import de.passbutler.common.base.Bindable
import de.passbutler.common.base.MutableBindable

/**
 * General binders
 */

fun <ViewType : View, BindableType> ViewType.bind(lifecycleOwner: LifecycleOwner, bindable: Bindable<BindableType>, block: ViewType.(BindableType) -> Unit) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        block(newValue)
    }
}

/**
 * Visibility binders
 */

fun <T> View.bindVisibility(lifecycleOwner: LifecycleOwner, bindable: Bindable<T>, block: (T) -> Boolean) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        visible = block(newValue)
    }
}

fun View.bindVisibility(lifecycleOwner: LifecycleOwner, bindable: Bindable<Boolean>) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        visible = newValue
    }
}

fun View.bindVisibility(lifecycleOwner: LifecycleOwner, bindable1: Bindable<Boolean>, bindable2: Bindable<Boolean>, block: (Boolean, Boolean) -> Boolean) {
    bindable1.addLifecycleObserver(lifecycleOwner, false) { newValue ->
        visible = block(newValue, bindable2.value)
    }

    bindable2.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        visible = block(bindable1.value, newValue)
    }
}

fun View.bindVisibility(
    lifecycleOwner: LifecycleOwner,
    bindable1: Bindable<Boolean>,
    bindable2: Bindable<Boolean>,
    bindable3: Bindable<Boolean>,
    block: (Boolean, Boolean, Boolean) -> Boolean
) {
    bindable1.addLifecycleObserver(lifecycleOwner, false) { newValue ->
        visible = block(newValue, bindable2.value, bindable3.value)
    }

    bindable2.addLifecycleObserver(lifecycleOwner, false) { newValue ->
        visible = block(bindable1.value, newValue, bindable3.value)
    }

    bindable3.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        visible = block(bindable1.value, bindable2.value, newValue)
    }
}

/**
 * Enabled binders
 */

fun View.bindEnabled(lifecycleOwner: LifecycleOwner, bindable: Bindable<Boolean>) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        isEnabled = newValue
    }
}

/**
 * Text binders
 */

fun TextView.bindText(lifecycleOwner: LifecycleOwner, bindable: Bindable<String>) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        text = newValue
    }
}

fun TextView.bindTextAndVisibility(lifecycleOwner: LifecycleOwner, bindable: Bindable<String?>) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        if (newValue != null) {
            text = newValue
            visible = true
        } else {
            text = ""
            visible = false
        }
    }
}

fun <T> TextView.bindTextAndVisibility(lifecycleOwner: LifecycleOwner, bindable: Bindable<T>, transform: (T) -> String?) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        val newTransformedValue = transform(newValue)

        if (newTransformedValue != null) {
            text = newTransformedValue
            visible = true
        } else {
            text = ""
            visible = false
        }
    }
}

/**
 * Input binders
 */

fun <T : String?> TextInputEditText.bindInput(lifecycleOwner: LifecycleOwner, bindable: MutableBindable<T>) {
    bindable.addLifecycleObserver(lifecycleOwner, true) { newValue ->
        if (text?.toString() != newValue) {
            setText(newValue)
            setSelection(newValue?.length ?: 0)
        }
    }

    addTextChangedListener { newText ->
        @Suppress("UNCHECKED_CAST")
        bindable.value = newText?.toString() as T
    }
}
