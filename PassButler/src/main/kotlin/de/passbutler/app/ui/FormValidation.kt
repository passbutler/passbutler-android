package de.passbutler.app.ui

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

data class FormFieldValidator(val formFieldLayout: TextInputLayout, val formField: TextInputEditText, val validationRules: List<Rule>) {
    data class Rule(val isInvalidValidator: (formFieldText: String?) -> Boolean, val errorString: String)
}

sealed class FormValidationResult {
    object Valid : FormValidationResult()
    class Invalid(val firstInvalidFormField: TextInputEditText) : FormValidationResult()
}

fun validateForm(formFieldValidators: List<FormFieldValidator>): FormValidationResult {
    var previousValidationErrorOccurred = false
    var firstInvalidFormField: TextInputEditText? = null

    // Resets errors first
    formFieldValidators.forEach { formFieldValidator ->
        formFieldValidator.formFieldLayout.error = null
    }

    for (formFieldValidator in formFieldValidators) {
        val formFieldLayout = formFieldValidator.formFieldLayout
        val formField = formFieldValidator.formField
        val formFieldText = formField.text?.toString()

        for (formFieldValidationRule in formFieldValidator.validationRules) {
            val validationFailed = formFieldValidationRule.isInvalidValidator.invoke(formFieldText)

            if (validationFailed) {
                formFieldLayout.error = formFieldValidationRule.errorString

                // Only the first error-related view must be set
                if (!previousValidationErrorOccurred) {
                    firstInvalidFormField = formField
                }

                previousValidationErrorOccurred = true
                break
            }
        }
    }

    return firstInvalidFormField?.let {
        FormValidationResult.Invalid(it)
    } ?: run {
        FormValidationResult.Valid
    }
}