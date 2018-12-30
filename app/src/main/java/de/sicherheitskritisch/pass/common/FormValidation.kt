package de.sicherheitskritisch.pass.common

import android.widget.EditText

data class FormFieldValidator(val formField: EditText, val validationRules: List<Rule>) {
    data class Rule(val isInvalidValidator: (formFieldText: String) -> Boolean, val errorString: String)
}

sealed class FormValidationResult {
    class Valid : FormValidationResult()
    class Invalid(val firstInvalidFormField: EditText) : FormValidationResult()
}

fun validateForm(formFieldValidators: List<FormFieldValidator>): FormValidationResult {
    var previousValidationErrorOccurred = false
    var firstInvalidFormField: EditText? = null

    // Resets errors first
    formFieldValidators.forEach { formFieldValidator ->
        formFieldValidator.formField.error = null
    }

    for (formFieldValidator in formFieldValidators) {
        val formField = formFieldValidator.formField
        val formFieldText = formField.text.toString()

        for (formFieldValidationRule in formFieldValidator.validationRules) {
            val validationFailed = formFieldValidationRule.isInvalidValidator.invoke(formFieldText)

            if (validationFailed) {
                formField.error = formFieldValidationRule.errorString

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
        FormValidationResult.Valid()
    }
}