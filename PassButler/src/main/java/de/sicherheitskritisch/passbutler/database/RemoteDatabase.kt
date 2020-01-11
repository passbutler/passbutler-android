package de.sicherheitskritisch.passbutler.database

import android.net.Uri
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.LoggedInStateStorage
import de.sicherheitskritisch.passbutler.UserType
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.L
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.Route
import org.json.JSONArray
import org.json.JSONException
import retrofit2.Call
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
import java.time.Duration

typealias OkHttpResponse = okhttp3.Response

private const val API_VERSION_PREFIX = "v1"
private val API_TIMEOUT_CONNECT = Duration.ofSeconds(5)
private val API_TIMEOUT_READ = Duration.ofSeconds(5)
private val API_TIMEOUT_WRITE = Duration.ofSeconds(5)

interface AuthWebservice {
    @GET("/$API_VERSION_PREFIX/token")
    fun getToken(): Call<AuthToken>

    /**
     * Using `Interceptor` instead `Authenticator` because every request of webservice must be always authenticated.
     */
    private class PasswordAuthenticationInterceptor(username: String, password: String) : Interceptor {
        private var authorizationHeaderValue = Credentials.basic(username, password)

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
            val request = chain.request()
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", authorizationHeaderValue)
                .build()
            return chain.proceed(authenticatedRequest)
        }
    }

    private class DefaultConverterFactory : Converter.Factory() {
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
        @Throws(IllegalArgumentException::class)
        suspend fun create(serverUrl: Uri, username: String, password: String): AuthWebservice {
            require(!(BuildType.isReleaseBuild && !serverUrl.isHttpsScheme)) { "For release build, only TLS server URL are accepted!" }

            return withContext(Dispatchers.IO) {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(API_TIMEOUT_CONNECT)
                    .readTimeout(API_TIMEOUT_READ)
                    .writeTimeout(API_TIMEOUT_WRITE)
                    .addInterceptor(PasswordAuthenticationInterceptor(username, password))
                    .build()

                val retrofitBuilder = Retrofit.Builder()
                    .baseUrl(serverUrl.toString())
                    .client(okHttpClient)
                    .addConverterFactory(DefaultConverterFactory())
                    .addCallAdapterFactory(CoroutineCallAdapterFactory())
                    .build()

                retrofitBuilder.create(AuthWebservice::class.java)
            }
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

    private interface AuthTokenProvider {
        var authToken: AuthToken?
    }

    private class DefaultAuthTokenProvider(private val loggedInStateStorage: LoggedInStateStorage) : AuthTokenProvider {
        private val remoteUserType = loggedInStateStorage.userType as? UserType.Remote ?: throw IllegalStateException("The logged-in user type must be remote!")

        override var authToken: AuthToken?
            get() = remoteUserType.authToken
            set(value) {
                remoteUserType.authToken = value

                runBlocking {
                    loggedInStateStorage.persist()
                }
            }
    }

    /**
     * Adds authentication token to request if available.
     */
    private class AuthTokenInterceptor(
        loggedInStateStorage: LoggedInStateStorage
    ) : Interceptor, AuthTokenProvider by DefaultAuthTokenProvider(loggedInStateStorage) {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
            val actualRequest = chain.request()
            val interceptedRequest = authToken?.let { currentAuthToken ->
                actualRequest.applyTokenAuthorizationHeader(currentAuthToken.token)
            } ?: actualRequest

            return chain.proceed(interceptedRequest)
        }
    }

    /**
     * Automatically requests new auth token if not available or rejected by server.
     * It always try to request a new token, despite "Authorization" header is present because server rejected token anyway.
     */
    private class AuthTokenAuthenticator(
        private val authWebservice: AuthWebservice,
        private val loggedInStateStorage: LoggedInStateStorage
    ) : Authenticator, AuthTokenProvider by DefaultAuthTokenProvider(loggedInStateStorage) {
        @Throws(IOException::class)
        override fun authenticate(route: Route?, response: OkHttpResponse): Request? {
            val actualRequest = response.request()
            L.d("AuthTokenAuthenticator", "authenticate(): Re-authenticate request ${actualRequest.url()} ")

            val newAuthTokenResult = authWebservice.getToken().execute().completeRequestWithResult()

            return when (newAuthTokenResult) {
                is Success -> {
                    L.d("AuthTokenAuthenticator", "authenticate(): The new token was requested successfully")
                    val newAuthToken = newAuthTokenResult.result
                    authToken = newAuthToken

                    actualRequest.applyTokenAuthorizationHeader(newAuthToken.token)
                }
                is Failure -> {
                    L.w("AuthTokenAuthenticator", "authenticate(): The new token could not be requested!", newAuthTokenResult.throwable)
                    actualRequest
                }
            }
        }
    }

    private class UnitConverterFactory : Converter.Factory() {
        private val unitConverter = Converter<ResponseBody, Unit> {
            it.close()
        }

        override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
            return if (type == Unit::class) unitConverter else null
        }
    }

    private class DefaultConverterFactory : Converter.Factory() {
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

        @Throws(IllegalArgumentException::class)
        suspend fun create(serverUrl: Uri, authWebservice: AuthWebservice, loggedInStateStorage: LoggedInStateStorage): UserWebservice {
            require(!(BuildType.isReleaseBuild && !serverUrl.isHttpsScheme)) { "For release build, only TLS server URL are accepted!" }

            return withContext(Dispatchers.IO) {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(API_TIMEOUT_CONNECT)
                    .readTimeout(API_TIMEOUT_READ)
                    .writeTimeout(API_TIMEOUT_WRITE)
                    .addInterceptor(AuthTokenInterceptor(loggedInStateStorage))
                    .authenticator(AuthTokenAuthenticator(authWebservice, loggedInStateStorage))
                    .build()

                val retrofitBuilder = Retrofit.Builder()
                    .baseUrl(serverUrl.toString())
                    .client(okHttpClient)
                    .addConverterFactory(UnitConverterFactory())
                    .addConverterFactory(DefaultConverterFactory())
                    .addCallAdapterFactory(CoroutineCallAdapterFactory())
                    .build()

                retrofitBuilder.create(UserWebservice::class.java)
            }
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

private fun Request.applyTokenAuthorizationHeader(token: String): Request {
    return newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
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
