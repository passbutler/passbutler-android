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
import de.passbutler.app.R
import de.passbutler.app.ui.ScreenPresentingExtensions.Companion.instanceIdentification
import de.passbutler.common.ItemViewModel
import de.passbutler.common.unlockedItemData
import org.tinylog.kotlin.Logger

class AutofillMainActivity : AppCompatActivity() {

    var rootFragment: AutofillRootFragment? = null

    lateinit var structureParserResult: StructureParser.Result

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val receivedStructureParserResult = createStructureParserResult()

        if (receivedStructureParserResult != null) {
            structureParserResult = receivedStructureParserResult
        } else {
            Logger.warn("The necessary StructureParser.Result could not be created from received intent!")
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        setContentView(R.layout.activity_autofill_main)

        val autofillRootFragmentTag = AutofillRootFragment::class.java.instanceIdentification
        rootFragment = supportFragmentManager.findFragmentByTag(autofillRootFragmentTag) as? AutofillRootFragment

        if (rootFragment == null) {
            rootFragment = AutofillRootFragment.newInstance().also {
                val fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.frameLayout_activity_autofill_main_content_container, it, autofillRootFragmentTag)
                fragmentTransaction.commit()
            }
        }
    }

    private fun createStructureParserResult(): StructureParser.Result? {
        val receivedIntent = intent
        return StructureParser.Result.create(
            receivedIntent.getStringExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_APPLICATION_ID),
            receivedIntent.getStringExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_WEB_DOMAIN),
            receivedIntent.getParcelableExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_USERNAME_ID),
            receivedIntent.getParcelableExtra(INTENT_EXTRA_STRUCTURE_PARSER_RESULT_PASSWORD_ID)
        )
    }

    fun itemWasSelected(itemViewModels: List<ItemViewModel>) {
        sendAutofillResponseIntent(itemViewModels)
    }

    private fun sendAutofillResponseIntent(itemViewModels: List<ItemViewModel>) {
        val responseIntent = Intent().apply {
            val autofillResponse = createAutofillResponse(structureParserResult, itemViewModels)
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, autofillResponse)
        }

        setResult(Activity.RESULT_OK, responseIntent)
        finish()
    }

    private fun createAutofillResponse(structureParserResult: StructureParser.Result, itemViewModels: List<ItemViewModel>): FillResponse {
        val autofillResponseBuilder = FillResponse.Builder()

        itemViewModels
            .take(AUTOFILL_ENTRIES_MAXIMUM)
            .forEach { itemViewModel ->
                val dataset = createDataset(structureParserResult, itemViewModel)
                autofillResponseBuilder.addDataset(dataset)
            }

        val saveInfo = createSaveInfo(structureParserResult)
        autofillResponseBuilder.setSaveInfo(saveInfo)

        return autofillResponseBuilder.build()
    }

    private fun createDataset(structureParserResult: StructureParser.Result, itemViewModel: ItemViewModel): Dataset {
        val itemData = itemViewModel.unlockedItemData

        val title = itemData.title
        val username = itemData.username
        val password = itemData.password

        val datasetBuilder = Dataset.Builder().apply {
            if (structureParserResult is StructureParser.Result.UsernameWithPassword) {
                val usernamePresentation = createDatasetEntryPresentation(getString(R.string.autofill_entry_label_username, title))
                setValue(structureParserResult.usernameId, AutofillValue.forText(username), usernamePresentation)
            }

            val passwordPresentation = createDatasetEntryPresentation(getString(R.string.autofill_entry_label_password, title))
            setValue(structureParserResult.passwordId, AutofillValue.forText(password), passwordPresentation)
        }

        return datasetBuilder.build()
    }

    private fun createDatasetEntryPresentation(entryTitle: String): RemoteViews {
        return RemoteViews(packageName, R.layout.list_item_autofill_entry).apply {
            setTextViewText(R.id.textView_autofill_entry_item, entryTitle)
        }
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

        private const val AUTOFILL_ENTRIES_MAXIMUM = 5

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

