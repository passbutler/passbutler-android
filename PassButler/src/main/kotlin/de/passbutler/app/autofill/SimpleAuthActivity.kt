package de.passbutler.app.autofill

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import de.passbutler.app.R

class SimpleAuthActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_service_auth_activity)
        findViewById<View>(R.id.yes).setOnClickListener { onYes() }
        findViewById<View>(R.id.no).setOnClickListener { onNo() }
    }

    private fun onYes() {
        val receivedIntent = intent
        val responseIntent = Intent()

        // TODO:
        //  1. authenticate if needed
        //  2. check if `applicationId` or `webDomain` could be found to show a) selection or b) send result back

        val structureParserResult = StructureParser.Result.create(
            receivedIntent.getStringExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID),
            receivedIntent.getStringExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN),
            receivedIntent.getParcelableExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID),
            receivedIntent.getParcelableExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID)
        )

        val intentResult = if (structureParserResult != null) {
            val autofillResponse = createAutofillResponse(structureParserResult)
            responseIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, autofillResponse)

            RESULT_OK
        } else {
            RESULT_CANCELED
        }

        setResult(intentResult, responseIntent)
        finish()
    }

    private fun createAutofillResponse(structureParserResult: StructureParser.Result): FillResponse {
        val autofillResponse = FillResponse.Builder()

        for (i in 1..AUTOFILL_ENTRIES_MAXIMUM) {
            val unlockedDataset: Dataset = createDataset(structureParserResult)
            autofillResponse.addDataset(unlockedDataset)
        }

        var saveInfo = SaveInfo.SAVE_DATA_TYPE_GENERIC
        val requiredIds = mutableListOf<AutofillId>()

        if (structureParserResult is StructureParser.Result.UsernameWithPassword) {
            saveInfo = saveInfo or SaveInfo.SAVE_DATA_TYPE_USERNAME
            requiredIds.add(structureParserResult.usernameId)
        }

        saveInfo = saveInfo or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        requiredIds.add(structureParserResult.passwordId)

        autofillResponse.setSaveInfo(
            SaveInfo.Builder(saveInfo, requiredIds.toTypedArray()).build()
        )

        return autofillResponse.build()
    }

    private fun createDataset(structureParserResult: StructureParser.Result): Dataset {
        // TODO: Remove
        val username = "foobar"
        val password = "1234"

        val datasetBuilder = Dataset.Builder().apply {
            if (structureParserResult is StructureParser.Result.UsernameWithPassword) {
                val usernamePresentation = RemoteViews(packageName, R.layout.list_item_autofill_entry)
                usernamePresentation.setTextViewText(R.id.textView_autofill_entry_item, getString(R.string.autofill_remote_view_label_username, username))

                setValue(structureParserResult.usernameId, AutofillValue.forText(username), usernamePresentation)
            }

            val passwordPresentation = RemoteViews(packageName, R.layout.list_item_autofill_entry)
            passwordPresentation.setTextViewText(R.id.textView_autofill_entry_item, getString(R.string.autofill_remote_view_label_password, username))

            setValue(structureParserResult.passwordId, AutofillValue.forText(password), passwordPresentation)
        }

        return datasetBuilder.build()
    }

    private fun onNo() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        var pendingIntentId = 0
            private set

        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID"
        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN"
        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID"
        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID"
        private const val AUTOFILL_ENTRIES_MAXIMUM = 5

        fun createAuthenticationIntentSender(
            context: Context,
            structureParserResult: StructureParser.Result
        ): IntentSender {
            val authenticateActivityIntent = Intent(context, SimpleAuthActivity::class.java).apply {
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID, structureParserResult.applicationId)
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN, structureParserResult.webDomain)
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID, (structureParserResult as? StructureParser.Result.UsernameWithPassword)?.usernameId)
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID, structureParserResult.passwordId)
            }

            return PendingIntent.getActivity(context, ++pendingIntentId, authenticateActivityIntent, PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        }
    }
}
