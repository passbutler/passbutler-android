package de.sicherheitskritisch.passbutler.common

import de.sicherheitskritisch.passbutler.models.User

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
}
