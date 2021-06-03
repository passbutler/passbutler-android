package de.passbutler.app.ui

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.passbutler.app.R
import de.passbutler.app.databinding.DialogPasswordGeneratorBinding
import de.passbutler.common.crypto.PasswordGenerator
import de.passbutler.common.crypto.PasswordGenerator.CharacterType.Digits
import de.passbutler.common.crypto.PasswordGenerator.CharacterType.Lowercase
import de.passbutler.common.crypto.PasswordGenerator.CharacterType.Symbols
import de.passbutler.common.crypto.PasswordGenerator.CharacterType.Uppercase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class PasswordGeneratorDialogBuilder(
    presentingFragment: BaseFragment,
    private val positiveClickAction: (newPassword: String) -> Unit,
    private val negativeClickAction: (() -> Unit)? = null
) : MaterialAlertDialogBuilder(presentingFragment.requireContext()),
    CoroutineScope by presentingFragment,
    CharacterTypeExtensions {

    private var binding: DialogPasswordGeneratorBinding

    private var generatePasswordJob: Job? = null
    private var generatePassword: String? = null

    init {
        val layoutInflater = LayoutInflater.from(context)
        binding = DialogPasswordGeneratorBinding.inflate(layoutInflater).apply {
            setupLengthSlider()
            setupRegenerateIcon()
            setupCharacterTypesSelection()
        }

        setView(binding.root)

        setupButtonSection()

        // Initially generate password
        regeneratePassword()
    }

    private fun DialogPasswordGeneratorBinding.setupLengthSlider() {
        sliderPasswordLength.apply {
            stepSize = 1.toFloat()
            valueFrom = PASSWORD_LENGTH_RANGE.first.toFloat()
            valueTo = PASSWORD_LENGTH_RANGE.last.toFloat()
            value = PASSWORD_LENGTH_DEFAULT.toFloat()

            addOnChangeListener { _, _, _ ->
                regeneratePassword()
            }
        }
    }

    private fun DialogPasswordGeneratorBinding.setupRegenerateIcon() {
        imageViewIcon.setOnClickListener {
            regeneratePassword()
        }
    }

    private fun DialogPasswordGeneratorBinding.setupCharacterTypesSelection() {
        checkBoxLowercase.setupCharacterTypeCheckBox(Lowercase)
        checkBoxUppercase.setupCharacterTypeCheckBox(Uppercase)
        checkBoxDigits.setupCharacterTypeCheckBox(Digits)
        checkBoxSymbols.setupCharacterTypeCheckBox(Symbols)
    }

    private fun MaterialCheckBox.setupCharacterTypeCheckBox(characterType: PasswordGenerator.CharacterType) {
        text = characterType.userfacingText(context)
        isChecked = characterType.isDefaultSelected

        setOnCheckedChangeListener { _, _ ->
            regeneratePassword()
        }
    }

    private fun setupButtonSection() {
        setPositiveButton(context.getString(R.string.general_accept)) { _, _ ->
            val generatePassword = generatePassword

            if (generatePassword != null) {
                positiveClickAction.invoke(generatePassword)
            } else {
                Logger.warn("The generated password is null!")
            }
        }

        setNegativeButton(context.getString(R.string.general_cancel)) { _, _ ->
            negativeClickAction?.invoke()
        }

        setOnDismissListener {
            negativeClickAction?.invoke()
        }
    }

    private fun regeneratePassword() {
        generatePasswordJob?.cancel()
        generatePasswordJob = launch {
            val passwordLength = binding.sliderPasswordLength.value.toInt().takeIf { it > 0 }
            val characterTypes = setOfNotNull(
                Lowercase.takeIf { binding.checkBoxLowercase.isChecked },
                Uppercase.takeIf { binding.checkBoxUppercase.isChecked },
                Digits.takeIf { binding.checkBoxDigits.isChecked },
                Symbols.takeIf { binding.checkBoxSymbols.isChecked }
            ).takeIf { it.isNotEmpty() }

            if (passwordLength != null && characterTypes != null) {
                val newGeneratePassword = PasswordGenerator.generatePassword(
                    length = passwordLength,
                    characterTypes = characterTypes
                )

                generatePassword = newGeneratePassword

                binding.textViewGeneratedPassword.text = newGeneratePassword
                binding.textViewGeneratedPassword.setTextColor(context.resolveThemeAttributeData(android.R.attr.textColorPrimary))
            } else {
                generatePassword = null

                binding.textViewGeneratedPassword.text = context.getString(R.string.passwordgenerator_dialog_missing_character_types_error)
                binding.textViewGeneratedPassword.setTextColor(context.resolveThemeAttributeData(R.attr.colorWarning))
            }
        }
    }

    companion object {
        private val PASSWORD_LENGTH_RANGE = 4..64
        private const val PASSWORD_LENGTH_DEFAULT = 18
    }
}

interface CharacterTypeExtensions {
    fun PasswordGenerator.CharacterType.userfacingText(context: Context): String {
        val stringKey = when (this) {
            Lowercase -> R.string.passwordgenerator_dialog_character_type_lowercase
            Uppercase -> R.string.passwordgenerator_dialog_character_type_uppercase
            Digits -> R.string.passwordgenerator_dialog_character_type_digits
            Symbols -> R.string.passwordgenerator_dialog_character_type_symbols
        }

        return context.getString(stringKey)
    }

    val PasswordGenerator.CharacterType.isDefaultSelected: Boolean
        get() = when (this) {
            Lowercase -> true
            Uppercase -> true
            Digits -> true
            Symbols -> false
        }
}
