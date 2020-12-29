package de.passbutler.app.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.ViewStructure
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import org.tinylog.kotlin.Logger
import java.util.*

/**
 * This structure parser was inspired by implementation of KeePassDX:
 * https://github.com/Kunzisoft/KeePassDX/blob/master/app/src/main/java/com/kunzisoft/keepass/autofill/StructureParser.kt
 */
@RequiresApi(api = Build.VERSION_CODES.O)
class StructureParser(private val assistStructure: AssistStructure) {

    fun parse(): Result? {
        val internalResult = InternalResult()

        for (i in 0 until assistStructure.windowNodeCount) {
            val windowNode = assistStructure.getWindowNodeAt(i)

            val applicationId = windowNode.title.toString().split("/").firstOrNull()
            Logger.debug("Received application id '$applicationId'.")
            internalResult.applicationId = applicationId

            if (parseViewNode(windowNode.rootViewNode, internalResult)) {
                break
            }
        }

        // If no explicit username field was found, use the guessed username field
        if (internalResult.usernameId == null && internalResult.passwordId != null && internalResult.usernameIdCandidate != null) {
            internalResult.usernameId = internalResult.usernameIdCandidate
        }

        return Result.create(internalResult.applicationId, internalResult.webDomain, internalResult.usernameId, internalResult.passwordId)
    }

    private fun parseViewNode(viewNode: AssistStructure.ViewNode, internalResult: InternalResult): Boolean {
        viewNode.webDomain?.takeIf { it.isNotEmpty() }?.let {
            internalResult.webDomain = it
            Logger.debug("Received web domain '$it'.")
        }

        // Only process visible nodes
        if (viewNode.visibility == View.VISIBLE) {
            val autofillId = viewNode.autofillId

            if (autofillId != null && viewNode.autofillType == View.AUTOFILL_TYPE_TEXT) {
                val autofillHints = viewNode.autofillHints

                when {
                    autofillHints?.isNotEmpty() == true && parseViewNodeByAutofillHints(viewNode, autofillId, autofillHints.toList(), internalResult) -> {
                        return true
                    }
                    parseViewNodeByHtmlAttributes(viewNode, autofillId, internalResult) -> {
                        return true
                    }
                    parseViewNodeByAndroidInput(viewNode, autofillId, internalResult) -> {
                        return true
                    }
                }
            }

            for (i in 0 until viewNode.childCount) {
                if (parseViewNode(viewNode.getChildAt(i), internalResult)) {
                    return true
                }
            }
        }

        return false
    }

    private fun parseViewNodeByAutofillHints(viewNode: AssistStructure.ViewNode, autofillId: AutofillId, autofillHints: List<String>, internalResult: InternalResult): Boolean {
        autofillHints.forEach { autofillHint ->
            when {
                AUTOFILL_USERNAME_HINTS.containsIgnoreCase(autofillHint) -> {
                    Logger.debug("The autofill id '$autofillId' was detected as username.")
                    internalResult.usernameId = autofillId
                }
                AUTOFILL_HINTS_PASSWORD.containsIgnoreCase(autofillHint) || autofillHint.contains("password", true) -> {
                    Logger.debug("The autofill id '$autofillId' was detected as password.")
                    internalResult.passwordId = autofillId

                    return true
                }
                AUTOFILL_AUTOCOMPLETE_IGNORE_HINTS.containsIgnoreCase(autofillHint) -> {
                    Logger.debug("The autofill id '$autofillId' was detected with 'autocomplete' setting.")
                    return parseViewNodeByHtmlAttributes(viewNode, autofillId, internalResult)
                }
                else -> {
                    Logger.info("The autofill id '$autofillId' was detected as unknown autofill hint!")
                }
            }
        }

        return false
    }

    private fun parseViewNodeByHtmlAttributes(viewNode: AssistStructure.ViewNode, autofillId: AutofillId, internalResult: InternalResult): Boolean {
        val viewNodeHtmlInfo = viewNode.htmlInfo

        // Only process `input` HTML tags
        if (viewNodeHtmlInfo?.isInputFormTag() == true) {
            viewNodeHtmlInfo.attributes
                ?.filter { tagAttribute ->
                    val tagAttributeName = tagAttribute.first.toLowerCaseWithEnglishLocale()

                    // Only process `type` attributes
                    tagAttributeName == HTML_ATTRIBUTE_TYPE
                }
                ?.map { tagAttribute ->
                    val tagAttributeValue = tagAttribute.second.toLowerCaseWithEnglishLocale()
                    tagAttributeValue
                }
                ?.forEach { tagAttributeValue ->
                    when {
                        HTML_ATTRIBUTES_USERNAME.containsIgnoreCase(tagAttributeValue) -> {
                            Logger.debug("The autofill id '$autofillId' was detected as username.")
                            internalResult.usernameId = autofillId
                        }
                        HTML_ATTRIBUTES_USERNAME_CANDIDATE.containsIgnoreCase(tagAttributeValue) -> {
                            Logger.debug("The autofill id '$autofillId' was detected as username candidate.")
                            internalResult.usernameIdCandidate = autofillId
                        }
                        HTML_ATTRIBUTES_PASSWORD.containsIgnoreCase(tagAttributeValue) -> {
                            Logger.debug("The autofill id '$autofillId' was detected as password.")
                            internalResult.passwordId = autofillId

                            return true
                        }
                    }
                }
        }

        return false
    }

    private fun parseViewNodeByAndroidInput(viewNode: AssistStructure.ViewNode, autofillId: AutofillId, internalResult: InternalResult): Boolean {
        val viewNodeInputType = viewNode.inputType

        // Only process text input views
        return if (viewNodeInputType.hasFlag(InputType.TYPE_CLASS_TEXT)) {
            when {
                INPUT_TYPES_USERNAME.containsInputType(viewNodeInputType) -> {
                    Logger.debug("The autofill id '$autofillId' was detected as username.")
                    internalResult.usernameId = autofillId
                    false
                }
                INPUT_TYPES_USERNAME_CANDIDATE.containsInputType(viewNodeInputType) -> {
                    Logger.debug("The autofill id '$autofillId' was detected as username candidate.")
                    internalResult.usernameIdCandidate = autofillId
                    false
                }
                INPUT_TYPES_PASSWORD.containsInputType(viewNodeInputType) -> {
                    Logger.debug("The autofill id '$autofillId' was detected as password.")
                    internalResult.passwordId = autofillId
                    true
                }
                INPUT_TYPES_IGNORED.containsInputType(viewNodeInputType) -> {
                    Logger.debug("The autofill id '$autofillId' was detected as ignored input type.")
                    false
                }
                else -> {
                    Logger.info("The autofill id '$autofillId' was detected as unknown input type!")
                    false
                }
            }
        } else {
            false
        }
    }

    private class InternalResult {
        var applicationId: String? = null

        var webDomain: String? = null
            set(value) {
                // Only apply value if still unset
                if (field == null) {
                    field = value
                }
            }

        var usernameId: AutofillId? = null
            set(value) {
                // Only apply value if still unset
                if (field == null) {
                    field = value
                }
            }

        var usernameIdCandidate: AutofillId? = null
            set(value) {
                // Only apply value if still unset
                if (field == null) {
                    field = value
                }
            }

        var passwordId: AutofillId? = null
            set(value) {
                // Only apply value if still unset
                if (field == null) {
                    field = value
                }
            }
    }

    sealed class Result(val applicationId: String?, val webDomain: String?, val passwordId: AutofillId) {
        class UsernameWithPassword(applicationId: String?, webDomain: String?, val usernameId: AutofillId, passwordId: AutofillId) : Result(applicationId, webDomain, passwordId)
        class PasswordOnly(applicationId: String?, webDomain: String?, passwordId: AutofillId) : Result(applicationId, webDomain, passwordId)

        val allAutofillIds
            get() = listOfNotNull(
                (this as? UsernameWithPassword)?.usernameId,
                (this as? UsernameWithPassword)?.passwordId ?: (this as? PasswordOnly)?.passwordId
            )

        companion object {
            fun create(applicationId: String?, webDomain: String?, usernameId: AutofillId?, passwordId: AutofillId?): Result? {
                return when {
                    usernameId != null && passwordId != null -> UsernameWithPassword(applicationId, webDomain, usernameId, passwordId)
                    passwordId != null -> PasswordOnly(applicationId, webDomain, passwordId)
                    else -> null
                }
            }
        }
    }

    companion object {
        private val AUTOFILL_USERNAME_HINTS = listOf(
            View.AUTOFILL_HINT_USERNAME,
            View.AUTOFILL_HINT_EMAIL_ADDRESS,
            "email",
            "usernameOrEmail"
        )

        private val AUTOFILL_HINTS_PASSWORD = listOf(
            View.AUTOFILL_HINT_PASSWORD
        )

        private val AUTOFILL_AUTOCOMPLETE_IGNORE_HINTS = listOf(
            "on",
            "off"
        )

        private const val HTML_ATTRIBUTE_TYPE = "type"

        private val HTML_ATTRIBUTES_USERNAME = listOf(
            "tel",
            "email"
        )

        private val HTML_ATTRIBUTES_USERNAME_CANDIDATE = listOf(
            "text"
        )

        private val HTML_ATTRIBUTES_PASSWORD = listOf(
            "password"
        )

        private val INPUT_TYPES_USERNAME = listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        private val INPUT_TYPES_USERNAME_CANDIDATE = listOf(
            InputType.TYPE_TEXT_VARIATION_NORMAL,
            InputType.TYPE_NUMBER_VARIATION_NORMAL,
            InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        )

        private val INPUT_TYPES_PASSWORD = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )

        private val INPUT_TYPES_IGNORED = listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_PHONETIC,
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
    }
}

internal fun List<String>.containsIgnoreCase(search: String): Boolean {
    return any { element ->
        element.toLowerCaseWithEnglishLocale() == search.toLowerCaseWithEnglishLocale()
    }
}

internal fun String.toLowerCaseWithEnglishLocale(): String {
    return toLowerCase(Locale.ENGLISH)
}

internal fun ViewStructure.HtmlInfo.isInputFormTag(): Boolean {
    return tag.contentEquals("input")
}

typealias InputTypeConstant = Int

internal fun List<InputTypeConstant>.containsInputType(inputType: InputTypeConstant): Boolean {
    return any { element ->
        element.hasFlag(inputType)
    }
}

internal fun Int.hasFlag(inputType: InputTypeConstant): Boolean {
    return this and inputType != 0
}
