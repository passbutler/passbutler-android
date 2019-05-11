package de.sicherheitskritisch.passbutler.database

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import android.content.Context
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserDao
import de.sicherheitskritisch.passbutler.database.models.UserModelConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

@Database(entities = [User::class], version = 1, exportSchema = false)
@TypeConverters(GeneralDatabaseConverters::class, UserModelConverters::class)
abstract class PassButlerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

class GeneralDatabaseConverters {
    @TypeConverter
    fun longToDate(long: Long?) = long?.let { Date(it) }

    @TypeConverter
    fun dateToLong(date: Date?) = date?.time
}

class PassButlerRepository(applicationContext: Context) {

    private val localDatabase by lazy {
        Room.databaseBuilder(applicationContext, PassButlerDatabase::class.java, "PassButlerDatabase").build()
    }

    suspend fun findAllUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            localDatabase.userDao().findAll()
        }
    }

    suspend fun findUser(username: String): User? {
        return withContext(Dispatchers.IO) {
            localDatabase.userDao().findUser(username)
        }
    }

    suspend fun insertUser(vararg user: User) {
        withContext(Dispatchers.IO) {
            localDatabase.userDao().insert(*user)
        }
    }

    suspend fun updateUser(vararg user: User) {
        withContext(Dispatchers.IO) {
            localDatabase.userDao().update(*user)
        }
    }

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            localDatabase.clearAllTables()
        }
    }
}
