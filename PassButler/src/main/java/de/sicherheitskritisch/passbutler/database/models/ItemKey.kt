package de.sicherheitskritisch.passbutler.database.models

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg itemKey: ItemKey)

    @Query("SELECT * FROM itemkeys")
    fun findAll(): List<ItemKey>

    @Query("SELECT * FROM itemkeys WHERE userUsername = :username")
    fun findUserItemKeys(username: String): List<ItemKey>
}