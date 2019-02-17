package de.sicherheitskritisch.passbutler.common

fun collectNewUserItems(currentUsers: List<User>, newUsers: List<User>): List<User> {
    return newUsers.filter { newUsersElement ->
        // The element should not be contained in current users (identified via primary field)
        !currentUsers.any { it.username == newUsersElement.username }
    }
}
