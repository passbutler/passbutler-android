package de.sicherheitskritisch.passbutler.common

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import de.sicherheitskritisch.passbutler.models.User
import de.sicherheitskritisch.passbutler.models.UserDao
import java.util.*

@Database(entities = [User::class], version = 1, exportSchema = false)
@TypeConverters(DatabaseConverters::class)
abstract class PassDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

class DatabaseConverters {
    @TypeConverter
    fun longToDate(long: Long?) = long?.let { Date(it) }

    @TypeConverter
    fun dateToLong(date: Date?) = date?.time

    @TypeConverter
    fun booleanToInt(boolean: Boolean?) = boolean?.let { if (it) 1 else 0 }

    @TypeConverter
    fun intToBoolean(int: Int?) = int?.let { it == 1 }
}