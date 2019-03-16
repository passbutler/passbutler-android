package de.sicherheitskritisch.passbutler.common

import java.util.*

object Synchronization {
    /**
     * Detects new items between two lists. To detect new items, the item primary key must be specified (e.g. `username` or `id`).
     */
    fun <T : Synchronizable> collectNewItems(currentItems: List<T>, newItems: List<T>): List<T> {
        return newItems.filter { newItem ->
            // The element should not be contained in current items (identified via primary field)
            !currentItems.any { it.primaryField == newItem.primaryField }
        }
    }

    /**
     * Collects a list of items of modified items (determined by modified date) based on current item and potentially updated item list.
     */
    fun <T : Synchronizable> collectModifiedUserItems(currentItems: List<T>, updatedItems: List<T>): List<T> {
        if (currentItems.size != updatedItems.size) {
            throw IllegalArgumentException("The current user list and updated user list size must be the same!")
        }

        val sortedCurrentItems = currentItems.sortedBy { it.primaryField }
        val sortedUpdatedItems = updatedItems.sortedBy { it.primaryField }

        return sortedCurrentItems.mapIndexedNotNull { index, currentUserItem ->
            val updatedItem = sortedUpdatedItems[index]

            if (currentUserItem.primaryField != updatedItem.primaryField) {
                throw IllegalStateException("The current item list and updated item list must contain the same items!")
            }

            if (updatedItem.modified > currentUserItem.modified) {
                updatedItem
            } else {
                null
            }
        }
    }
}

interface Synchronizable {
    val primaryField: String
    var modified: Date
}
