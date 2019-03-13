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

    /**
     * Collects a list of items of modified items (determined by modified date) based on
     * current item and potentially updated item list.
     */
    fun collectModifiedUserItems(currentUsers: List<User>, updatedUsers: List<User>): List<User> {
        if (currentUsers.size != updatedUsers.size) {
            throw IllegalArgumentException("The current user list and updated user list size must be the same!")
        }

        val sortedCurrentUsers = currentUsers.sortedBy { it.username }
        val sortedUpdatedUsers = updatedUsers.sortedBy { it.username }

        return sortedCurrentUsers.mapIndexedNotNull { index, currentUserItem ->
            val updatedUserItem = sortedUpdatedUsers[index]

            if (currentUserItem.username != updatedUserItem.username) {
                throw IllegalStateException("The current user list and updated user list must contain the same users!")
            }

            if (updatedUserItem.modified > currentUserItem.modified) {
                updatedUserItem
            } else {
                null
            }
        }
    }
}
