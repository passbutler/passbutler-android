package de.sicherheitskritisch.passbutler.database

import de.sicherheitskritisch.passbutler.database.Synchronization.collectModifiedUserItems
import de.sicherheitskritisch.passbutler.database.Synchronization.collectNewItems
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class SynchronizationTest {

    /**
     * `collectNewItems()` tests
     */

    @Test
    fun `If no newUsers list nor currentUsers list are given, an empty list is returned`() {
        val currentUsers = listOf<User>()
        val newUsers = listOf<User>()

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectNewItems(currentUsers, newUsers))
    }

    @Test
    fun `If the newUsers list contains no user, an empty list is returned`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val newUsers = emptyList<User>()

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectNewItems(currentUsers, newUsers))
    }

    @Test
    fun `If the newUsers list contains only the currentUsers list, an empty list is returned`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val newUsers = currentUsers.toList()

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectNewItems(currentUsers, newUsers))
    }

    @Test
    fun `If the newUsers list contains only 1 new user, a list containing only the new user is returned`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val newUsers = listOf(
            createUser("user c")
        )

        val expectedUsers = newUsers.toList()
        assertEquals(expectedUsers, collectNewItems(currentUsers, newUsers))
    }

    @Test
    fun `If the newUsers list contains the currentUsers list and a new user, a list containing only the new user is returned`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val newUsers = listOf(
            createUser("user a"),
            createUser("user b"),
            createUser("user c")
        )

        val expectedUsers = listOf(
            createUser("user c")
        )

        assertEquals(expectedUsers, collectNewItems(currentUsers, newUsers))
    }

    /**
     * `collectModifiedUserItems()` tests
     */

    @Test
    fun `If the list size is different, an exception is thrown`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val updatedUsers = listOf(
            createUser("user a")
        )

        assertThrows(IllegalArgumentException::class.java) {
            collectModifiedUserItems(currentUsers, updatedUsers)
        }
    }

    @Test
    fun `If the list contains different user items, an exception is thrown`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val updatedUsers = listOf(
            createUser("user a"),
            createUser("user c")
        )

        assertThrows(IllegalStateException::class.java) {
            collectModifiedUserItems(currentUsers, updatedUsers)
        }
    }

    @Test
    fun `If the updated list is the same as the currentUsers list list, an empty list is returned`() {
        val currentUsers = listOf(
            createUser("user a", modified = "2019-03-12T10:15:00Z"),
            createUser("user b", modified = "2019-03-12T10:15:00Z")
        )

        val updatedUsers = currentUsers.toList()

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectModifiedUserItems(currentUsers, updatedUsers))
    }

    @Test
    fun `If no user in the updated list has newer modified date, an empty list is returned`() {
        val currentUsers = listOf(
            createUser("user a", modified = "2019-03-13T10:15:00Z"),
            createUser("user b", modified = "2019-03-13T10:15:00Z")
        )

        val updatedUsers = listOf(
            createUser("user a", modified = "2019-03-12T10:15:00Z"),
            createUser("user b", modified = "2019-03-12T10:15:00Z")
        )

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectModifiedUserItems(currentUsers, updatedUsers))
    }

    @Test
    fun `If one user item was changed, it is returned`() {
        val currentUsers = listOf(
            createUser("user a", modified = "2019-03-12T10:15:00Z"),
            createUser("user b", modified = "2019-03-12T10:15:00Z")
        )

        // Only user B was modified
        val updatedUsers = listOf(
            createUser("user a", modified = "2019-03-12T10:15:00Z"),
            createUser("user b", modified = "2019-03-12T15:15:00Z")
        )

        val expectedUsers = listOf(
            createUser("user b", modified = "2019-03-12T15:15:00Z")
        )

        assertEquals(expectedUsers, collectModifiedUserItems(currentUsers, updatedUsers))
    }

    @Test
    fun `If multiple user items were changed, they are returned`() {
        val currentUsers = listOf(
            createUser("user a", modified = "2019-03-12T10:15:00Z"),
            createUser("user b", modified = "2019-03-12T10:15:00Z")
        )

        // Both user A and B were modified
        val updatedUsers = listOf(
            createUser("user a", modified = "2019-03-13T10:15:00Z"),
            createUser("user b", modified = "2019-03-12T15:15:00Z")
        )

        val expectedUsers = listOf(
            createUser("user a", modified = "2019-03-13T10:15:00Z"),
            createUser("user b", modified = "2019-03-12T15:15:00Z")
        )

        assertEquals(expectedUsers, collectModifiedUserItems(currentUsers, updatedUsers))
    }
}

private fun createUser(userName: String, modified: String? = null): User {
    val currentDate = Date.from(Instant.parse("2019-03-12T10:00:00Z"))

    val testMasterKeyDerivationInformation = UserTest.createTestKeyDerivationInformation()
    val testProtectedValueMasterEncryptionKey = UserTest.createTestProtectedValueMasterEncryptionKey()
    val testProtectedValueSettings = UserTest.createTestProtectedValueSettings()

    return User(
        userName,
        testMasterKeyDerivationInformation,
        testProtectedValueMasterEncryptionKey,
        testProtectedValueSettings,
        false,
        modified?.let { Date.from(Instant.parse(it)) } ?: currentDate,
        currentDate
    )
}
