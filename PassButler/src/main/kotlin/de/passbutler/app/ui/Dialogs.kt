package de.passbutler.app.ui

import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.passbutler.app.R

fun BaseFragment.showEditTextDialog(
    title: String,
    positiveClickAction: (String?) -> Unit,
    negativeClickAction: (() -> Unit)? = null
): AlertDialog? {
    return context?.let { fragmentContext ->
        val builder = MaterialAlertDialogBuilder(fragmentContext)
        builder.setTitle(title)

        val textInputLayout = TextInputLayout(fragmentContext).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(
                    resources.getDimensionPixelSize(fragmentContext.resolveThemeAttributeId(R.attr.dialogPreferredPadding)),
                    resources.getDimensionPixelSize(fragmentContext.resolveThemeAttributeId(R.attr.marginM)),
                    resources.getDimensionPixelSize(fragmentContext.resolveThemeAttributeId(R.attr.dialogPreferredPadding)),
                    0
                )
            }
        }

        val textInputEditText = TextInputEditText(textInputLayout.context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        textInputLayout.addView(textInputEditText)

        // Be sure, the `inputType` is set first to make `END_ICON_PASSWORD_TOGGLE` as `endIconMode` work properly
        textInputEditText.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
        textInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE

        val editTextContainer = FrameLayout(fragmentContext).also {
            it.addView(textInputLayout)
        }

        builder.setView(editTextContainer)

        builder.setPositiveButton(getString(R.string.general_okay)) { _, _ ->
            positiveClickAction(textInputEditText.text?.toString())
        }

        builder.setNegativeButton(getString(R.string.general_cancel)) { _, _ ->
            negativeClickAction?.invoke()
        }

        builder.setOnDismissListener {
            negativeClickAction?.invoke()
        }

        builder.create().also {
            // Enforce the keyboard to show up if any view requests focus
            it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            it.show()

            textInputEditText.requestFocus()
        }
    }
}

fun BaseFragment.showConfirmDialog(
    title: String,
    positiveActionTitle: String,
    positiveClickAction: () -> Unit,
    negativeClickAction: (() -> Unit)? = null
) {
    context?.let { fragmentContext ->
        val builder = MaterialAlertDialogBuilder(fragmentContext).apply {
            setTitle(title)

            setPositiveButton(positiveActionTitle) { _, _ ->
                positiveClickAction()
            }

            setNegativeButton(getString(R.string.general_cancel)) { _, _ ->
                negativeClickAction?.invoke()
            }

            setOnDismissListener {
                negativeClickAction?.invoke()
            }
        }

        builder.show()
    }
}

fun BaseFragment.showDangerousConfirmDialog(
    title: String,
    message: String,
    positiveActionTitle: String,
    positiveClickAction: () -> Unit,
    negativeClickAction: (() -> Unit)? = null
) {
    context?.let { fragmentContext ->
        val builder = MaterialAlertDialogBuilder(fragmentContext, R.style.ThemeOverlay_PassButler_DangerousAlertDialogTheme).apply {
            setTitle(title)
            setMessage(message)

            setPositiveButton(positiveActionTitle) { _, _ ->
                positiveClickAction()
            }

            setNegativeButton(getString(R.string.general_cancel)) { _, _ ->
                negativeClickAction?.invoke()
            }

            setOnDismissListener {
                negativeClickAction?.invoke()
            }
        }

        builder.show()
    }
}
