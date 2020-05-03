package de.passbutler.app.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.sqldelight.android.AndroidSqliteDriver
import de.passbutler.common.database.LOCAL_DATABASE_SQL_FOREIGN_KEYS_ENABLE
import de.passbutler.common.database.LocalRepository
import de.passbutler.common.database.PassButlerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun createLocalRepository(applicationContext: Context): LocalRepository {
    return withContext(Dispatchers.IO) {
        val sqlDriverCallback = object : AndroidSqliteDriver.Callback(PassButlerDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL(LOCAL_DATABASE_SQL_FOREIGN_KEYS_ENABLE)
            }
        }

        val databaseName = "${applicationContext.filesDir.path}/PassButlerDatabase.sqlite"
        val driver = AndroidSqliteDriver(PassButlerDatabase.Schema, applicationContext, databaseName, callback = sqlDriverCallback)

        val localDatabase = PassButlerDatabase(driver)
        LocalRepository(localDatabase)
    }
}