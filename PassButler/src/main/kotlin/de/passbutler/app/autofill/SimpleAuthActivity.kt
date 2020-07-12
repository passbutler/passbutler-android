package de.passbutler.app.autofill

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.View
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

        val structureParserResult = receivedIntent.getParcelableExtra<StructureParser.Result>(INTENT_EXTRA_STRUCTURE_PARSER_RESULT)

        val intentResult = if (structureParserResult != null) {
            val autofillResponse = createAutofillResponse(this, structureParserResult)
            responseIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, autofillResponse)

            RESULT_OK
        } else {
            RESULT_CANCELED
        }

        setResult(intentResult, responseIntent)
        finish()
    }

    private fun onNo() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        var pendingIntentId = 0
            private set

        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT"
        private const val AUTOFILL_ENTRIES_MAXIMUM = 5

        fun createAuthenticationIntentSender(
            context: Context,
            structureParserResult: StructureParser.Result
        ): IntentSender {
            val authenticateActivityIntent = Intent(context, SimpleAuthActivity::class.java).apply {
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT, structureParserResult)
            }

            return PendingIntent.getActivity(context, ++pendingIntentId, authenticateActivityIntent, PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        }

        fun createAutofillResponse(
            context: Context,
            structureParserResult: StructureParser.Result
        ): FillResponse {
            val autofillResponse = FillResponse.Builder()

            for (i in 1..AUTOFILL_ENTRIES_MAXIMUM) {
                val unlockedDataset: Dataset = createDataset(context, structureParserResult)
                autofillResponse.addDataset(unlockedDataset)
            }

            // 2.Add save info
//            val ids: Collection<AutofillId> = fields.values()
//            val requiredIds = arrayOfNulls<AutofillId>(ids.size)
//            ids.toArray<AutofillId>(requiredIds)
//
//            response.setSaveInfo( // We're simple, so we're generic
//                SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC, requiredIds).build()
//            )

            // 3.Profit!
            return autofillResponse.build()
        }

        private fun createDataset(
            context: Context,
            structureParserResult: StructureParser.Result
        ): Dataset {
            val packageName = context.packageName

            // TODO: Remove
            val username = "foobar"
            val password = "1234"

            val usernamePresentation = RemoteViews(packageName, R.layout.list_item_autofill_entry)
            usernamePresentation.setTextViewText(R.id.textView_autofill_entry_item, context.getString(R.string.autofill_remote_view_label_username, username))

            val passwordPresentation = RemoteViews(packageName, R.layout.list_item_autofill_entry)
            passwordPresentation.setTextViewText(R.id.textView_autofill_entry_item, context.getString(R.string.autofill_remote_view_label_password, username))

            val datasetBuilder = Dataset.Builder()

            structureParserResult.usernameId?.let { autofillId ->
                datasetBuilder.setValue(autofillId, AutofillValue.forText(username), usernamePresentation)
            }

            structureParserResult.passwordId?.let { autofillId ->
                datasetBuilder.setValue(autofillId, AutofillValue.forText(password), passwordPresentation)
            }

            return datasetBuilder.build()
        }
    }
}
