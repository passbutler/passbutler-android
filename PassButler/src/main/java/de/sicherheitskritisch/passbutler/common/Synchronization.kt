package de.sicherheitskritisch.passbutler.common

import de.sicherheitskritisch.passbutler.io.requestTextResource
import de.sicherheitskritisch.passbutler.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

object Synchronization {
    /**
     * Detects new items between two lists with users. To detect new items, the primary key `username` is used.
     */
    fun collectNewUserItems(currentUsers: List<User>, newUsers: List<User>): List<User> {
        return newUsers.filter { newUsersElement ->
            // The element should not be contained in current users (identified via primary field)
            !currentUsers.any { it.username == newUsersElement.username }
        }
    }

    /**
     * Fetches the user list from remote server and builds up the list of `User`.
     */
    suspend fun fetchRemoteUsers(userListEndpointUrl: String): List<User> {
        return withContext(Dispatchers.IO) {
            requestTextResource(userListEndpointUrl)?.let { fetchedUserListJson ->
                try {
                    JSONArray(fetchedUserListJson).asJSONObjectSequence().mapNotNull { userJSONObject ->
                        User.deserialize(userJSONObject)
                    }.toList()
                } catch (e: JSONException) {
                    L.w("Synchronization", "fetchRemoteUsers(): The remote fetched user list could not be created!", e)
                    null
                }
            } ?: emptyList()
        }
    }
}
