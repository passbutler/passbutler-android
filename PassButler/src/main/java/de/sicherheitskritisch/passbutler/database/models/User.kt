package de.sicherheitskritisch.passbutler.database.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONSerializableDeserializer
import de.sicherheitskritisch.passbutler.base.getJSONSerializable
import de.sicherheitskritisch.passbutler.base.getJSONSerializableOrNull
import de.sicherheitskritisch.passbutler.base.getStringOrNull
import de.sicherheitskritisch.passbutler.base.putBoolean
import de.sicherheitskritisch.passbutler.base.putJSONSerializable
import de.sicherheitskritisch.passbutler.base.putLong
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.database.Synchronizable
import org.json.JSONException
import org.json.JSONObject
import java.util.*

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val username: String,
    val masterPasswordAuthenticationHash: String?,
    val masterKeyDerivationInformation: KeyDerivationInformation?,
    val masterEncryptionKey: ProtectedValue<CryptographicKey>?,
    val itemEncryptionPublicKey: CryptographicKey,
    val itemEncryptionSecretKey: ProtectedValue<CryptographicKey>?,
    val settings: ProtectedValue<UserSettings>?,
    override var deleted: Boolean,
    override var modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    @Ignore
    override val primaryField = username

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_USERNAME, username)
            putString(SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH, masterPasswordAuthenticationHash)
            putJSONSerializable(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION, masterKeyDerivationInformation)
            putJSONSerializable(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY, masterEncryptionKey)
            putJSONSerializable(SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY, itemEncryptionPublicKey)
            putJSONSerializable(SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY, itemEncryptionSecretKey)
            putJSONSerializable(SERIALIZATION_KEY_SETTINGS, settings)
            putBoolean(SERIALIZATION_KEY_DELETED, deleted)
            putLong(SERIALIZATION_KEY_MODIFIED, modified.time)
            putLong(SERIALIZATION_KEY_CREATED, created.time)
        }
    }

    // TODO: Fix type inferation issues
    // TODO: Have multiple `Deserializer` to reflect public and current user
    object Deserializer : JSONSerializableDeserializer<User>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): User {
            return User(
                username = jsonObject.getString(SERIALIZATION_KEY_USERNAME),
                masterPasswordAuthenticationHash = jsonObject.getStringOrNull(SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH),
                masterKeyDerivationInformation = jsonObject.getJSONSerializableOrNull(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION, KeyDerivationInformation.Deserializer),
                masterEncryptionKey = jsonObject.getJSONSerializableOrNull(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY, ProtectedValue.Deserializer<CryptographicKey>()),
                itemEncryptionPublicKey = jsonObject.getJSONSerializable(SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY, CryptographicKey.Deserializer),
                itemEncryptionSecretKey = jsonObject.getJSONSerializableOrNull(SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY, ProtectedValue.Deserializer<CryptographicKey>()),
                settings = jsonObject.getJSONSerializableOrNull(SERIALIZATION_KEY_SETTINGS, ProtectedValue.Deserializer<UserSettings>()),
                deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                modified = Date(jsonObject.getLong(SERIALIZATION_KEY_MODIFIED)),
                created = Date(jsonObject.getLong(SERIALIZATION_KEY_CREATED))
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_USERNAME = "username"
        private const val SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH = "masterPasswordAuthenticationHash"
        private const val SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION = "masterKeyDerivationInformation"
        private const val SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY = "masterEncryptionKey"
        private const val SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY = "itemEncryptionPublicKey"
        private const val SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY = "itemEncryptionSecretKey"
        private const val SERIALIZATION_KEY_SETTINGS = "settings"
        private const val SERIALIZATION_KEY_DELETED = "deleted"
        private const val SERIALIZATION_KEY_MODIFIED = "modified"
        private const val SERIALIZATION_KEY_CREATED = "created"
    }
}