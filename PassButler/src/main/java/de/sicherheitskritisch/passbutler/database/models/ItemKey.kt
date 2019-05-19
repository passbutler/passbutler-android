package de.sicherheitskritisch.passbutler.database.models

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.Relation
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.database.Synchronizable
import org.json.JSONObject
import java.util.*

@Entity(
    tableName = "itemkeys",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = arrayOf("username"),
        childColumns = arrayOf("userUsername"),
        onDelete = ForeignKey.CASCADE)
    ]
)
data class ItemKey(
    @PrimaryKey
    val uuid: String,
    @ColumnInfo(name = "userUsername", index = true)
    val userUsername: String,
    override var deleted: Boolean,
    override var modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    @Ignore
    override val primaryField = uuid

    override fun serialize(): JSONObject {
        // TODO: Implement
        return JSONObject()
    }
}

class UserWithItemKeys {
    @Embedded
    var user: User? = null

    @Relation(parentColumn = "username", entityColumn = "userUsername", entity = ItemKey::class)
    var itemKeys: List<ItemKey>? = null
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg itemKey: ItemKey)

    @Query("SELECT * FROM itemkeys")
    fun findAll(): List<ItemKey>

    @Query("SELECT * FROM users WHERE username = :username")
    fun findUserItemKeys(username: String): UserWithItemKeys?
}