package com.grocer.billing.core.data.remote

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitClient(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kirana_network_prefs", Context.MODE_PRIVATE)
    private val authPrefs: SharedPreferences =
        context.getSharedPreferences("kirana_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000/"
    }

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

    fun getApiService(): SyncApiService {
        return currentApi ?: synchronized(this) {
            currentApi ?: buildRetrofit().create(SyncApiService::class.java).also {
                currentApi = it
            }
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
