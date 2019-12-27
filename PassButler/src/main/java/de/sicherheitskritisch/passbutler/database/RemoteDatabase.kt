package de.sicherheitskritisch.passbutler.database

import android.net.Uri
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.asJSONObjectSequence
import de.sicherheitskritisch.passbutler.base.isHttpsScheme
import de.sicherheitskritisch.passbutler.base.serialize
import de.sicherheitskritisch.passbutler.crypto.models.AuthToken
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.ItemAuthorization
import de.sicherheitskritisch.passbutler.database.models.User
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

typealias OkHttpResponse = okhttp3.Response

private const val API_VERSION_PREFIX = "v1"

interface AuthWebservice {
    @GET("/$API_VERSION_PREFIX/token")
    fun getTokenAsync(): Deferred<Response<AuthToken>>

    private class ConverterFactory : Converter.Factory() {
        override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
            return when (type) {
                AuthToken::class.java -> createAuthTokenResponse()
                else -> null
            }
        }

        @Throws(JSONException::class)
        private fun createAuthTokenResponse() = Converter<ResponseBody, AuthToken> {
            AuthToken.Deserializer.deserialize(it.string())
        }
    }

    companion object {
        fun create(serverUrl: Uri, username: String, password: String): AuthWebservice {
            require(!(BuildType.isReleaseBuild && !serverUrl.isHttpsScheme)) { "For release build, only TLS server URL are accepted!" }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(PasswordAuthenticationInterceptor(username, password))
                .build()

            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(serverUrl.toString())
                .client(okHttpClient)
                .addConverterFactory(ConverterFactory())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .build()

            return retrofitBuilder.create(AuthWebservice::class.java)
        }
    }
}

suspend fun AuthWebservice?.requestAuthToken(): Result<AuthToken> {
    val authWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val getTokenRequest = authWebservice?.getTokenAsync()
            val getTokenResponse = getTokenRequest?.await()
            getTokenResponse.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

interface UserWebservice {
    @GET("/$API_VERSION_PREFIX/users")
    fun getUsersAsync(): Deferred<Response<List<User>>>

    @GET("/$API_VERSION_PREFIX/user")
    fun getUserDetailsAsync(): Deferred<Response<User>>

    @PUT("/$API_VERSION_PREFIX/user")
    fun setUserDetailsAsync(@Body user: User): Deferred<Response<Unit>>

    @GET("/$API_VERSION_PREFIX/user/itemauthorizations")
    fun getUserItemAuthorizationsAsync(): Deferred<Response<List<ItemAuthorization>>>

    @PUT("/$API_VERSION_PREFIX/user/itemauthorizations")
    fun setUserItemAuthorizationsAsync(@Body itemAuthorizations: List<ItemAuthorization>): Deferred<Response<Unit>>

    @GET("/$API_VERSION_PREFIX/user/items")
    fun getUserItemsAsync(): Deferred<Response<List<Item>>>

    @PUT("/$API_VERSION_PREFIX/user/items")
    fun setUserItemsAsync(@Body items: List<Item>): Deferred<Response<Unit>>

    private class ConverterFactory : Converter.Factory() {
        override fun requestBodyConverter(type: Type, parameterAnnotations: Array<Annotation>, methodAnnotations: Array<Annotation>, retrofit: Retrofit): Converter<*, RequestBody>? {
            return when {
                type == User::class.java -> createUserRequestConverter()
                type.isListType(ItemAuthorization::class.java) -> createItemAuthorizationListRequestConverter()
                type.isListType(Item::class.java) -> createItemListRequestConverter()
                else -> null
            }
        }

        private fun createUserRequestConverter() = Converter<User, RequestBody> {
            RequestBody.create(MEDIA_TYPE_JSON, it.serialize().toString())
        }

        private fun createItemAuthorizationListRequestConverter() = Converter<List<ItemAuthorization>, RequestBody> {
            RequestBody.create(MEDIA_TYPE_JSON, it.serialize().toString())
        }

        private fun createItemListRequestConverter() = Converter<List<Item>, RequestBody> {
            RequestBody.create(MEDIA_TYPE_JSON, it.serialize().toString())
        }

        override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
            return when {
                type == User::class.java -> createUserResponseConverter()
                type.isListType(User::class.java) -> createUserListResponseConverter()
                type.isListType(ItemAuthorization::class.java) -> createItemAuthorizationListResponseConverter()
                type.isListType(Item::class.java) -> createItemListResponseConverter()
                else -> null
            }
        }

        @Throws(JSONException::class)
        private fun createUserResponseConverter() = Converter<ResponseBody, User> {
            User.DefaultUserDeserializer.deserialize(it.string())
        }

        @Throws(JSONException::class)
        private fun createUserListResponseConverter() = Converter<ResponseBody, List<User>> {
            JSONArray(it.string()).asJSONObjectSequence().mapNotNull { userJsonObject ->
                User.PartialUserDeserializer.deserialize(userJsonObject)
            }.toList()
        }

        @Throws(JSONException::class)
        private fun createItemAuthorizationListResponseConverter() = Converter<ResponseBody, List<ItemAuthorization>> {
            JSONArray(it.string()).asJSONObjectSequence().mapNotNull { itemAuthorizationJsonObject ->
                ItemAuthorization.Deserializer.deserialize(itemAuthorizationJsonObject)
            }.toList()
        }

        @Throws(JSONException::class)
        private fun createItemListResponseConverter() = Converter<ResponseBody, List<Item>> {
            JSONArray(it.string()).asJSONObjectSequence().mapNotNull { itemJsonObject ->
                Item.Deserializer.deserialize(itemJsonObject)
            }.toList()
        }
    }

    companion object {
        private val MEDIA_TYPE_JSON = MediaType.get("application/json")

        fun create(serverUrl: Uri, authToken: String): UserWebservice {
            require(!(BuildType.isReleaseBuild && !serverUrl.isHttpsScheme)) { "For release build, only TLS server URL are accepted!" }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(TokenAuthenticationInterceptor(authToken))
                .build()

            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(serverUrl.toString())
                .client(okHttpClient)
                .addConverterFactory(UnitConverterFactory())
                .addConverterFactory(ConverterFactory())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .build()

            return retrofitBuilder.create(UserWebservice::class.java)
        }
    }
}

suspend fun UserWebservice?.requestPublicUserList(): Result<List<User>> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val request = userWebservice?.getUsersAsync()
            val response = request?.await()
            response.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.requestUser(): Result<User> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val request = userWebservice?.getUserDetailsAsync()
            val response = request?.await()
            response.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.updateUser(user: User): Result<Unit> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val request = userWebservice?.setUserDetailsAsync(user)
            val response = request?.await()
            response.completeRequestWithoutResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.requestItemAuthorizationList(): Result<List<ItemAuthorization>> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val request = userWebservice?.getUserItemAuthorizationsAsync()
            val response = request?.await()
            response.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.updateItemAuthorizationList(itemAuthorizations: List<ItemAuthorization>): Result<Unit> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val request = userWebservice?.setUserItemAuthorizationsAsync(itemAuthorizations)
            val response = request?.await()
            response.completeRequestWithoutResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.requestItemList(): Result<List<Item>> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val request = userWebservice?.getUserItemsAsync()
            val response = request?.await()
            response.completeRequestWithResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

suspend fun UserWebservice?.updateItemList(items: List<Item>): Result<Unit> {
    val userWebservice = this
    return withContext(Dispatchers.IO) {
        try {
            val request = userWebservice?.setUserItemsAsync(items)
            val response = request?.await()
            response.completeRequestWithoutResult()
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

/**
 * Allows a Retrofit web service to authenticate with username/password based HTTP basic auth.
 */
private class PasswordAuthenticationInterceptor(username: String, password: String) : AuthenticationInterceptor() {
    override val authorizationHeaderValue: String = Credentials.basic(username, password)
}

/**
 * Allows a Retrofit web service to authenticate with token in HTTP "Authorization" header.
 */
private class TokenAuthenticationInterceptor(authToken: String) : AuthenticationInterceptor() {
    override val authorizationHeaderValue: String = "Bearer $authToken"
}

private abstract class AuthenticationInterceptor : Interceptor {
    protected abstract val authorizationHeaderValue: String

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val request = chain.request()
        val authenticatedRequest = request.newBuilder()
            .header("Authorization", authorizationHeaderValue).build()
        return chain.proceed(authenticatedRequest)
    }
}

/**
 * Allows to have "no content" responses like `Deferred<Response<Unit>>`.
 */
class UnitConverterFactory : Converter.Factory() {
    private val unitConverter = Converter<ResponseBody, Unit> {
        it.close()
    }

    override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        return if (type == Unit::class) unitConverter else null
    }
}

private fun <T> Response<T>?.completeRequestWithResult(): Result<T> {
    val responseResult = this?.body()

    return when {
        this?.isSuccessful == true && responseResult != null -> Success(responseResult)
        this?.code() == HTTP_UNAUTHORIZED -> Failure(RequestUnauthorizedException("The request in unauthorized ${this.technicalErrorDescription}"))
        this?.code() == HTTP_FORBIDDEN -> Failure(RequestForbiddenException("The request in forbidden ${this.technicalErrorDescription}"))
        else -> Failure(RequestFailedException("The request result could not be get ${this.technicalErrorDescription}"))
    }
}

private fun <T> Response<T>?.completeRequestWithoutResult(): Result<Unit> {
    return when {
        this?.isSuccessful == true -> Success(Unit)
        this?.code() == HTTP_UNAUTHORIZED -> Failure(RequestUnauthorizedException("The request in unauthorized ${this.technicalErrorDescription}"))
        this?.code() == HTTP_FORBIDDEN -> Failure(RequestForbiddenException("The request in forbidden ${this.technicalErrorDescription}"))
        else -> Failure(RequestFailedException("The request result could not be get ${this.technicalErrorDescription}"))
    }
}

private val Response<*>?.technicalErrorDescription
    get() = "(HTTP status code ${this?.code()}): ${this?.errorBody()?.string()?.minimized()}"

private fun String.minimized(): String {
    return this
        .trim()
        .replace("\n", "")
        .replace(" ", "")
}

private fun Type.isListType(clazz: Class<*>): Boolean {
    return (this as? ParameterizedType)?.let { it.rawType == List::class.java && it.actualTypeArguments.firstOrNull() == clazz } ?: false
}

class RequestUnauthorizedException(message: String? = null) : Exception(message)
class RequestForbiddenException(message: String? = null) : Exception(message)
class RequestFailedException(message: String? = null) : Exception(message)
