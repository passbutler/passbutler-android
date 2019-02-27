package de.sicherheitskritisch.passbutler.common

import de.sicherheitskritisch.passbutler.common.Synchronization.collectNewUserItems
import de.sicherheitskritisch.passbutler.models.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class SynchronizationTest {

    @Test
    fun `If no new users are given nor current users are given, an empty list is returned`() {
        val currentUsers = listOf<User>()
        val newUsers = listOf<User>()

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectNewUserItems(currentUsers, newUsers))
    }

    @Test
    fun `If the new users list contains no user, an empty list is returned`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val newUsers = emptyList<User>()

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectNewUserItems(currentUsers, newUsers))
    }

    @Test
    fun `If the new users list contains only the current users, an empty list is returned`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val newUsers = currentUsers.toList()

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectNewUserItems(currentUsers, newUsers))
    }

    @Test
    fun `If the new users list contains a user contained in current users, but with a modified non-primary field, an empty list is returned`() {
        val currentUsers = listOf(
            createUser("user a")
        )

        val newUsers = listOf(
            createUser("user a", 2)
        )

        val expectedUsers = emptyList<User>()
        assertEquals(expectedUsers, collectNewUserItems(currentUsers, newUsers))
    }

    @Test
    fun `If the new users list contains only 1 new user, a list containing only the new user is returned`() {
        val currentUsers = listOf(
            createUser("user a"),
            createUser("user b")
        )

        val newUsers = listOf(
            createUser("user c")
        )

        val expectedUsers = newUsers.toList()
        assertEquals(expectedUsers, collectNewUserItems(currentUsers, newUsers))
    }

    @Test
    fun `If the new users list contains the current users and a new user, a list containing only the new user is returned`() {
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

        assertEquals(expectedUsers, collectNewUserItems(currentUsers, newUsers))
    }
}

private fun createUser(userName: String, lockTimeout: Int = 1): User {
    val currentDate = Date.from(Instant.now())

    return User(
        username = userName,
        lockTimeout = lockTimeout,
        deleted = false,
        modified = currentDate,
        created = currentDate
    )
}