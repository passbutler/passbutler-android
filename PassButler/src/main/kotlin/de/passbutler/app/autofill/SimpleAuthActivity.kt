package de.passbutler.app.autofill

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.util.ArrayMap
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import de.passbutler.app.R


/**
 * Activity used for autofill authentication, it simply sets the dataste upon tapping OK.
 */
class SimpleAuthActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_service_auth_activity)
        findViewById<View>(R.id.yes).setOnClickListener { onYes() }
        findViewById<View>(R.id.no).setOnClickListener { onNo() }
    }

    private fun onYes() {
        val myIntent = intent
        val replyIntent = Intent()

        val dataset = myIntent.getParcelableExtra<Dataset>(EXTRA_DATASET)

        if (dataset != null) {
            replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        } else {
            val hints = myIntent.getStringArrayExtra(EXTRA_HINTS)
            val ids = myIntent.getParcelableArrayExtra(EXTRA_IDS)


            val size = hints.size
            val fields = ArrayMap<String, AutofillId>(size)

            for (i in 0 until size) {
                fields[hints[i]] = ids[i] as AutofillId
            }




            val shouldAuthenticateDatasets = myIntent.getBooleanExtra(EXTRA_AUTH_DATASETS, false)


            val response = createAutofillResponse(this, fields, 1, shouldAuthenticateDatasets)
            replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        }

        setResult(RESULT_OK, replyIntent)
        finish()
    }

    private fun onNo() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val EXTRA_DATASET = "dataset"
        private const val EXTRA_HINTS = "hints"
        private const val EXTRA_IDS = "ids"
        private const val EXTRA_AUTH_DATASETS = "auth_datasets"
        private var sPendingIntentId = 0

        fun newIntentSenderForDataset(
            context: Context,
            dataset: Dataset
        ): IntentSender {
            return newIntentSender(
                context = context,
                dataset = dataset,
                hints = null,
                ids = null,
                authenticateDatasets = false
            )
        }

        /**
         * Creates intent to get response back to the autofill service.
         */
        fun newIntentSenderForResponse(
            context: Context,
            hints: Array<String>,
            ids: Array<AutofillId>,
            authenticateDatasets: Boolean
        ): IntentSender {
            return newIntentSender(
                context = context,
                dataset = null,
                hints = hints,
                ids = ids,
                authenticateDatasets = authenticateDatasets
            )
        }

        private fun newIntentSender(
            context: Context,
            dataset: Dataset?,
            hints: Array<String>?,
            ids: Array<AutofillId>?,
            authenticateDatasets: Boolean
        ): IntentSender {
            val intent = Intent(context, SimpleAuthActivity::class.java)

            if (dataset != null) {
                intent.putExtra(EXTRA_DATASET, dataset)
            } else {
                intent.putExtra(EXTRA_HINTS, hints)
                intent.putExtra(EXTRA_IDS, ids)
                intent.putExtra(EXTRA_AUTH_DATASETS, authenticateDatasets)
            }

            return PendingIntent.getActivity(
                context, ++sPendingIntentId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            ).intentSender
        }

        fun createAutofillResponse(
            context: Context,
            fields: ArrayMap<String, AutofillId>,
            numDatasets: Int,
            authenticateDatasets: Boolean
        ): FillResponse {
            val packageName = context.packageName
            val response = FillResponse.Builder()

            // 1.Add the dynamic datasets
            for (i in 1..numDatasets) {
                val unlockedDataset: Dataset = newUnlockedDataset(fields, packageName, i)

                if (authenticateDatasets) {
                    val lockedDataset = Dataset.Builder()

                    for ((hint, id) in fields.entries) {
                        val value = "$i-$hint"

                        val authentication = newIntentSenderForDataset(context, unlockedDataset)
                        val presentation: RemoteViews = newDatasetPresentation(packageName, "Tap to auth $value")

                        lockedDataset.setValue(id, null, presentation)
                            .setAuthentication(authentication)
                    }
                    response.addDataset(lockedDataset.build())
                } else {
                    response.addDataset(unlockedDataset)
                }
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
            return response.build()
        }

        private fun newDatasetPresentation(
            packageName: String,
            text: CharSequence
        ): RemoteViews {
            return RemoteViews(packageName, R.layout.list_item_autofill_entry).apply {
                setTextViewText(R.id.textView_autofill_entry_item, text)
            }
        }

        private fun newUnlockedDataset(
            fields: Map<String, AutofillId>,
            packageName: String, i: Int
        ): Dataset {
            val dataset = Dataset.Builder()
            for ((hint, id) in fields) {
                val value = "$i-$hint"

                // We're simple - our dataset values are hardcoded as "N-hint" (for example,
                // "1-username", "2-username") and they're displayed as such, except if they're a
                // password
                val displayValue = if (hint.contains("password")) "password for #$i" else value
                val presentation = newDatasetPresentation(packageName, displayValue)
                dataset.setValue(id, AutofillValue.forText(value), presentation)
            }
            return dataset.build()
        }
    }
}
