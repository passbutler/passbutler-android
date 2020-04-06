package de.sicherheitskritisch.passbutler.database

import android.net.Uri
import de.sicherheitskritisch.passbutler.UserManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.Route
import org.json.JSONArray
import org.json.JSONException
import org.tinylog.kotlin.Logger
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
                    .build()

                retrofitBuilder.create(AuthWebservice::class.java)
            }
        }
    }
}

interface UserWebservice {
    @PUT("/$API_VERSION_PREFIX/register")
    suspend fun registerUser(@Body user: User): Response<Unit>

    @GET("/$API_VERSION_PREFIX/users")
    suspend fun getUsers(): Response<List<User>>

    @GET("/$API_VERSION_PREFIX/user")
    suspend fun getUserDetails(): Response<User>

    @PUT("/$API_VERSION_PREFIX/user")
    suspend fun setUserDetails(@Body user: User): Response<Unit>

    @GET("/$API_VERSION_PREFIX/user/itemauthorizations")
    suspend fun getUserItemAuthorizations(): Response<List<ItemAuthorization>>

    @PUT("/$API_VERSION_PREFIX/user/itemauthorizations")
    suspend fun setUserItemAuthorizations(@Body itemAuthorizations: List<ItemAuthorization>): Response<Unit>

    @GET("/$API_VERSION_PREFIX/user/items")
    suspend fun getUserItems(): Response<List<Item>>

    @PUT("/$API_VERSION_PREFIX/user/items")
    suspend fun setUserItems(@Body items: List<Item>): Response<Unit>

    private interface AuthTokenProvider {
        var authToken: AuthToken?
    }

    private class DefaultAuthTokenProvider(private val userManager: UserManager) : AuthTokenProvider {
        override var authToken: AuthToken?
            get() = userManager.authToken.value
            set(value) {
                runBlocking {
                    userManager.updateAuthToken(value)
                }
            }
    }

    /**
     * Adds authentication token to request if available.
     */
    private class AuthTokenInterceptor(
        userManager: UserManager
    ) : Interceptor, AuthTokenProvider by DefaultAuthTokenProvider(userManager) {
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
        userManager: UserManager
    ) : Authenticator, AuthTokenProvider by DefaultAuthTokenProvider(userManager) {
        @Throws(IOException::class)
        override fun authenticate(route: Route?, response: OkHttpResponse): Request? {
            val actualRequest = response.request
            Logger.debug("Re-authenticate request ${actualRequest.url} ")

            val newAuthTokenResult = authWebservice.getToken().execute().completeRequestWithResult()

            return when (newAuthTokenResult) {
                is Success -> {
                    Logger.debug("The new token was requested successfully")
                    val newAuthToken = newAuthTokenResult.result
                    authToken = newAuthToken

                    actualRequest.applyTokenAuthorizationHeader(newAuthToken.token)
                }
                is Failure -> {
                    Logger.warn(newAuthTokenResult.throwable, "The new token could not be requested - authentication failed")

                    // Give up here to avoid infinite re-authentication loop
                    null
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
            it.serialize().toString().toRequestBody(MEDIA_TYPE_JSON)
        }

        private fun createItemAuthorizationListRequestConverter() = Converter<List<ItemAuthorization>, RequestBody> {
            it.serialize().toString().toRequestBody(MEDIA_TYPE_JSON)
        }

        private fun createItemListRequestConverter() = Converter<List<Item>, RequestBody> {
            it.serialize().toString().toRequestBody(MEDIA_TYPE_JSON)
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
        private val MEDIA_TYPE_JSON = "application/json".toMediaType()

        @Throws(IllegalArgumentException::class)
        suspend fun create(serverUrl: Uri, authWebservice: AuthWebservice, userManager: UserManager): UserWebservice {
            require(!(BuildType.isReleaseBuild && !serverUrl.isHttpsScheme)) { "For release build, only TLS server URL are accepted!" }

            return withContext(Dispatchers.IO) {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(API_TIMEOUT_CONNECT)
                    .readTimeout(API_TIMEOUT_READ)
                    .writeTimeout(API_TIMEOUT_WRITE)
                    .addInterceptor(AuthTokenInterceptor(userManager))
                    .authenticator(AuthTokenAuthenticator(authWebservice, userManager))
                    .build()

                val retrofitBuilder = Retrofit.Builder()
                    .baseUrl(serverUrl.toString())
                    .client(okHttpClient)
                    .addConverterFactory(UnitConverterFactory())
                    .addConverterFactory(DefaultConverterFactory())
                    .build()

                retrofitBuilder.create(UserWebservice::class.java)
            }
        }
    }
}

suspend fun <T> UserWebservice.requestWithResult(block: suspend UserWebservice.() -> Response<T>): Result<T> {
    return block(this).completeRequestWithResult()
}

suspend fun UserWebservice.requestWithoutResult(block: suspend UserWebservice.() -> Response<Unit>): Result<Unit> {
    return block(this).completeRequestWithoutResult()
}

private fun Request.applyTokenAuthorizationHeader(token: String): Request {
    return newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
}

private fun <T> Response<T>.completeRequestWithResult(): Result<T> {
    val responseResult = body()
    val requestCode = code()

    return when {
        isSuccessful == true && responseResult != null -> Success(responseResult)
        requestCode == HTTP_UNAUTHORIZED -> Failure(RequestUnauthorizedException("The request in unauthorized ${this.technicalErrorDescription}"))
        requestCode == HTTP_FORBIDDEN -> Failure(RequestForbiddenException("The request in forbidden ${this.technicalErrorDescription}"))
        else -> Failure(RequestFailedException("The request result could not be get ${this.technicalErrorDescription}"))
    }
}

private fun Response<Unit>.completeRequestWithoutResult(): Result<Unit> {
    val requestCode = code()

    return when {
        isSuccessful == true -> Success(Unit)
        requestCode == HTTP_UNAUTHORIZED -> Failure(RequestUnauthorizedException("The request in unauthorized ${this.technicalErrorDescription}"))
        requestCode == HTTP_FORBIDDEN -> Failure(RequestForbiddenException("The request in forbidden ${this.technicalErrorDescription}"))
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
