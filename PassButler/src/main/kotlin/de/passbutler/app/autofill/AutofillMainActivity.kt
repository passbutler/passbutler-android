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
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import de.passbutler.app.ItemViewModel
import de.passbutler.app.R
import de.passbutler.app.ui.FragmentPresenter

class AutofillMainActivity : AppCompatActivity() {

    var rootFragment: AutofillRootFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autofill_main)

        val autofillRootFragmentTag = FragmentPresenter.getFragmentTag(AutofillRootFragment::class.java)
        rootFragment = supportFragmentManager.findFragmentByTag(autofillRootFragmentTag) as? AutofillRootFragment

        if (rootFragment == null) {
            rootFragment = AutofillRootFragment.newInstance().also {
                val fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.frameLayout_activity_autofill_main_content_container, it, autofillRootFragmentTag)
                fragmentTransaction.commit()
            }
        }
    }

    fun itemWasSelected(itemViewModel: ItemViewModel) {
        sendAutofillResponseIntent(itemViewModel)
    }

    private fun sendAutofillResponseIntent(itemViewModel: ItemViewModel) {
        val receivedIntent = intent
        val responseIntent = Intent()

        val structureParserResult = StructureParser.Result.create(
            receivedIntent.getStringExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID),
            receivedIntent.getStringExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN),
            receivedIntent.getParcelableExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID),
            receivedIntent.getParcelableExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID)
        )

        val intentResult = if (structureParserResult != null) {
            val autofillResponse = createAutofillResponse(structureParserResult, itemViewModel)
            responseIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, autofillResponse)

            Activity.RESULT_OK
        } else {
            Activity.RESULT_CANCELED
        }

        setResult(intentResult, responseIntent)
        finish()
    }

    private fun createAutofillResponse(structureParserResult: StructureParser.Result, itemViewModel: ItemViewModel): FillResponse {
        val autofillResponseBuilder = FillResponse.Builder()

        val dataset: Dataset = createDataset(structureParserResult, itemViewModel)
        autofillResponseBuilder.addDataset(dataset)

        val saveInfo = createSaveInfo(structureParserResult)
        autofillResponseBuilder.setSaveInfo(saveInfo)

        return autofillResponseBuilder.build()
    }

    private fun createDataset(structureParserResult: StructureParser.Result, itemViewModel: ItemViewModel): Dataset {
        val itemData = itemViewModel.itemData ?: throw IllegalStateException("The item data is null despite it was used in autofill!")

        val username = itemData.username
        val password = itemData.password

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

    private fun createSaveInfo(structureParserResult: StructureParser.Result): SaveInfo {
        var saveInfoType = SaveInfo.SAVE_DATA_TYPE_GENERIC
        val saveInfoAutofillIds = mutableListOf<AutofillId>()

        if (structureParserResult is StructureParser.Result.UsernameWithPassword) {
            saveInfoType = saveInfoType or SaveInfo.SAVE_DATA_TYPE_USERNAME
            saveInfoAutofillIds.add(structureParserResult.usernameId)
        }

        saveInfoType = saveInfoType or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        saveInfoAutofillIds.add(structureParserResult.passwordId)

        return SaveInfo.Builder(saveInfoType, saveInfoAutofillIds.toTypedArray()).build()
    }

    companion object {
        var pendingIntentId = 0
            private set

        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID"
        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN"
        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID"
        private const val INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID = "INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID"

        fun createAuthenticationIntentSender(
            context: Context,
            structureParserResult: StructureParser.Result
        ): IntentSender {
            val authenticateActivityIntent = Intent(context, AutofillMainActivity::class.java).apply {
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID, structureParserResult.applicationId)
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN, structureParserResult.webDomain)
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID, (structureParserResult as? StructureParser.Result.UsernameWithPassword)?.usernameId)
                putExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID, structureParserResult.passwordId)
            }

            return PendingIntent.getActivity(context, ++pendingIntentId, authenticateActivityIntent, PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        }
    }
}

