package de.passbutler.app.autofill

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import de.passbutler.app.R
import org.tinylog.kotlin.Logger

@RequiresApi(api = Build.VERSION_CODES.O)
class PassButlerAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        Logger.debug("The autofill service was requested.")

        val structureParserResult = request.fillContexts.lastOrNull()?.structure?.let { lastAssistStructure ->
            val parsedStructure = StructureParser(lastAssistStructure)
            parsedStructure.parse()
        }

        val fillResponse = if (structureParserResult != null) {
            val serviceContext = this
            val responseBuilder = FillResponse.Builder().apply {
                val allAutoFillIds = arrayOf(
                    structureParserResult.usernameId,
                    structureParserResult.passwordId
                )

                val authenticationIntentSender = SimpleAuthActivity.createAuthenticationIntentSender(serviceContext, structureParserResult)
                val remoteViews = RemoteViews(packageName, R.layout.list_item_autofill_unlock)

                setAuthentication(allAutoFillIds, authenticationIntentSender, remoteViews)
            }

            responseBuilder.build()
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