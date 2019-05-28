package de.sicherheitskritisch.passbutler.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import de.sicherheitskritisch.passbutler.database.models.ItemDao
import de.sicherheitskritisch.passbutler.database.models.ItemKey
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserDao
import de.sicherheitskritisch.passbutler.database.models.UserModelConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

@Database(entities = [User::class, ItemKey::class], version = 1, exportSchema = false)
@TypeConverters(GeneralDatabaseConverters::class, UserModelConverters::class)
abstract class PassButlerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
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





    suspend fun insertItemKey(vararg itemKey: ItemKey) {
        withContext(Dispatchers.IO) {
            localDatabase.itemDao().insert(*itemKey)
        }
    }


    suspend fun findAllItemKeys(): List<ItemKey> {
        return withContext(Dispatchers.IO) {
            localDatabase.itemDao().findAll()
        }
    }

    suspend fun findUserItemKeys(username: String): List<ItemKey> {
        return withContext(Dispatchers.IO) {
            localDatabase.itemDao().findUserItemKeys(username)
        }
    }

















}
