package de.sicherheitskritisch.passbutler.database.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.putBoolean
import de.sicherheitskritisch.passbutler.base.putLong
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.getProtectedValue
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.getCryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.getKeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.putCryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.putKeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.putProtectedValue
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
            putKeyDerivationInformation(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION, masterKeyDerivationInformation)
            putProtectedValue(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY, masterEncryptionKey)
            putCryptographicKey(SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY, itemEncryptionPublicKey)
            putProtectedValue(SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY, itemEncryptionSecretKey)
            putProtectedValue(SERIALIZATION_KEY_SETTINGS, settings)
            putBoolean(SERIALIZATION_KEY_DELETED, deleted)
            putLong(SERIALIZATION_KEY_MODIFIED, modified.time)
            putLong(SERIALIZATION_KEY_CREATED, created.time)
        }
    }

    companion object {
        fun deserialize(jsonObject: JSONObject): User? {
            return try {
                User(
                    username = jsonObject.getString(SERIALIZATION_KEY_USERNAME),
                    masterPasswordAuthenticationHash = jsonObject.getString(SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH),
                    masterKeyDerivationInformation = jsonObject.getKeyDerivationInformation(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION),
                    masterEncryptionKey = jsonObject.getProtectedValue(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY),
                    itemEncryptionPublicKey = jsonObject.getCryptographicKey(SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY) ?: throw JSONException("The mandatory value for '$SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY' could not be deserialized!"),
                    itemEncryptionSecretKey = jsonObject.getProtectedValue(SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY),
                    settings = jsonObject.getProtectedValue(SERIALIZATION_KEY_SETTINGS),
                    deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                    modified = Date(jsonObject.getLong(SERIALIZATION_KEY_MODIFIED)),
                    created = Date(jsonObject.getLong(SERIALIZATION_KEY_CREATED))
                )
            } catch (e: JSONException) {
                L.w("User", "The user could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }

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