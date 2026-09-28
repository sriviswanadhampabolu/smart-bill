package com.grocer.billing.core.data.remote

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class NetworkStatus {
    data object Online : NetworkStatus()
    data class OfflineMode(val reason: String, val timestamp: Long = System.currentTimeMillis()) : NetworkStatus()
}

class RetrofitClient(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kirana_network_prefs", Context.MODE_PRIVATE)
    private val authPrefs: SharedPreferences =
        context.getSharedPreferences("kirana_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000/"
    }

    val networkMonitorInterceptor = NetworkMonitorInterceptor(context)
    val isCloudConnected: StateFlow<Boolean> = networkMonitorInterceptor.isCloudConnected

    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Online)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    @Volatile
    private var currentApi: SyncApiService? = null

    fun getServerUrl(): String {
        val url = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        return if (url.endsWith("/")) url else "$url/"
    }

    fun setServerUrl(newUrl: String) {
        val cleaned = newUrl.trim()
        val finalUrl = if (cleaned.endsWith("/")) cleaned else "$cleaned/"
        prefs.edit().putString(KEY_SERVER_URL, finalUrl).apply()
        synchronized(this) {
            currentApi = null // Invalidate cached instance
        }
    }

    fun markOffline(reason: String) {
        _networkStatus.value = NetworkStatus.OfflineMode(reason)
        // Do not falsely lock into offline mode if device has active cellular or WiFi internet
        val hasInternet = NetworkMonitorInterceptor.isInternetAvailable(context)
        _isOfflineMode.value = !hasInternet
    }

    fun markOnline() {
        _networkStatus.value = NetworkStatus.Online
        _isOfflineMode.value = false
    }

    fun getApiService(): SyncApiService {
        return currentApi ?: synchronized(this) {
            currentApi ?: buildRetrofit().create(SyncApiService::class.java).also {
                currentApi = it
            }
        }
    }

    suspend fun checkCloudSyncHealth(): Result<HealthResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = getApiService().checkHealth()
            if (response.isSuccessful && response.body() != null) {
                markOnline()
                Result.success(response.body()!!)
            } else {
                val reason = "Server response: HTTP ${response.code()} (Offline Mode active)"
                markOffline(reason)
                Result.failure(Exception(reason))
            }
        } catch (e: Exception) {
            val reason = when (e) {
                is ConnectException -> "Server connection refused - Operating in Offline Mode"
                is SocketTimeoutException -> "Server connection timed out - Operating in Offline Mode"
                is UnknownHostException -> "No internet / DNS error - Operating in Offline Mode"
                else -> "Offline Mode: ${e.localizedMessage ?: "Network unreachable"}"
            }
            markOffline(reason)
            Result.failure(e)
        }
    }

    private fun buildRetrofit(): Retrofit {
        val authInterceptor = Interceptor { chain ->
            val token = authPrefs.getString("auth_token", null)
            val requestBuilder = chain.request().newBuilder()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(networkMonitorInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(getServerUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
