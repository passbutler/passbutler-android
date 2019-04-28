package de.sicherheitskritisch.passbutler.database

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import android.content.Context
import de.sicherheitskritisch.passbutler.crypto.ProtectedValueConverters
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.CoroutineContext

@Database(entities = [User::class], version = 1, exportSchema = false)
@TypeConverters(GeneralDatabaseConverters::class, ProtectedValueConverters::class)
abstract class PassButlerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

class GeneralDatabaseConverters {
    @TypeConverter
    fun longToDate(long: Long?) = long?.let { Date(it) }

    @TypeConverter
    fun dateToLong(date: Date?) = date?.time
}

class PassButlerRepository(applicationContext: Context) : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = SupervisorJob()

    private val localDatabase by lazy {
        Room.databaseBuilder(applicationContext, PassButlerDatabase::class.java, "PassButlerDatabase").build()
    }

    suspend fun findAllUsers(): List<User> {
        return withContext(coroutineContext) {
            localDatabase.userDao().findAll()
        }
    }

    suspend fun findUser(username: String): User? {
        return withContext(coroutineContext) {
            localDatabase.userDao().findUser(username)
        }
    }

    suspend fun insertUser(vararg user: User) {
        withContext(coroutineContext) {
            localDatabase.userDao().insert(*user)
        }
    }

    suspend fun updateUser(vararg user: User) {
        withContext(coroutineContext) {
            localDatabase.userDao().update(*user)
        }
    }

    suspend fun reset() {
        withContext(coroutineContext) {
            localDatabase.clearAllTables()
        }
    }
}
