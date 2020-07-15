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
import de.passbutler.app.base.AbstractPassButlerApplication
import org.tinylog.kotlin.Logger

@RequiresApi(api = Build.VERSION_CODES.O)
class PassButlerAutofillService : AutofillService() {

    // TODO: More security measures (package verification etc.)

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        Logger.debug("The autofill service was requested.")

        val userManager = AbstractPassButlerApplication.userManager
        val isUserLoggedIn = userManager.loggedInStateStorage.value != null

        val fillResponse = if (isUserLoggedIn) {
            val structureParserResult = request.fillContexts.lastOrNull()?.structure?.let { lastAssistStructure ->
                val parsedStructure = StructureParser(lastAssistStructure)
                parsedStructure.parse()
            }

            if (structureParserResult != null) {
                val serviceContext = this
                val responseBuilder = FillResponse.Builder().apply {
                    val authenticationIntentSender = AutofillMainActivity.createAuthenticationIntentSender(serviceContext, structureParserResult)
                    val remoteViews = RemoteViews(packageName, R.layout.list_item_autofill_unlock)
                    setAuthentication(structureParserResult.allAutofillIds.toTypedArray(), authenticationIntentSender, remoteViews)
                }

                responseBuilder.build()
            } else {
                Logger.debug("The autofill request could not be fulfilled.")
                null
            }
        } else {
            Logger.debug("The user is not logged-in - ignore request!")
            null
        }

        callback.onSuccess(fillResponse)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // TODO: Implement
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