package de.passbutler.app.autofill

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import de.passbutler.app.R
import org.tinylog.kotlin.Logger

@RequiresApi(api = Build.VERSION_CODES.O)
class PassButlerAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        Logger.debug("The autofill service was requested.")

        val parsedStructureResult = request.fillContexts.lastOrNull()?.structure?.let { lastAssistStructure ->
            val parsedStructure = StructureParser(lastAssistStructure)
            parsedStructure.parse()
        }

        val fillResponse = if (parsedStructureResult != null) {
            // TODO: Remove hardcoded values
            val (username: String, password: String) = UserData("test", "1234")

            val usernamePresentation = RemoteViews(packageName, R.layout.list_item_autofill_entry)
            usernamePresentation.setTextViewText(R.id.textView_autofill_entry_item, getString(R.string.autofill_remote_view_label_username, username))

            val passwordPresentation = RemoteViews(packageName, R.layout.list_item_autofill_entry)
            passwordPresentation.setTextViewText(R.id.textView_autofill_entry_item, getString(R.string.autofill_remote_view_label_password, username))

            val datasetBuilder = Dataset.Builder()

            parsedStructureResult.usernameId?.let { autofillId ->
                datasetBuilder.setValue(autofillId, AutofillValue.forText(username), usernamePresentation)
            }

            parsedStructureResult.passwordId?.let { autofillId ->
                datasetBuilder.setValue(autofillId, AutofillValue.forText(password), passwordPresentation)
            }

            FillResponse.Builder()
                .addDataset(datasetBuilder.build())
                .build()

        } else {
            Logger.debug("The autofill request could not be fulfilled.")
            null
        }

        callback.onSuccess(fillResponse)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        Logger.debug("The autofill service does not support save requests!")
        callback.onFailure(null)
    }

    override fun onConnected() {
        Logger.debug("The autofill service was connected.")
    }

    override fun onDisconnected() {
        Logger.debug("The autofill service was disconnected.")
    }
}

data class UserData(var username: String, var password: String)
