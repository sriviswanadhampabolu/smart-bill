package com.grocer.billing.core.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Network monitor interceptor that accurately tracks network state.
 * Uses ConnectivityManager.getActiveNetworkCapabilities to detect actual hardware
 * cellular/WiFi connectivity rather than falsely flagging offline when a local server refuses connection.
 */
class NetworkMonitorInterceptor(
    private val context: Context
) : Interceptor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // Initializer defaults to true so screens don't start in false offline mode
    private val _isCloudConnected = MutableStateFlow(true)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    init {
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isCloudConnected.value = true
                }

                override fun onLost(network: Network) {
                    _isCloudConnected.value = isInternetAvailable(context)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    _isCloudConnected.value = hasInternet
                }
            })
        } catch (_: Exception) {
            // Fallback: evaluate current capabilities
            _isCloudConnected.value = isInternetAvailable(context)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            val response = chain.proceed(request)
            if (response.isSuccessful) {
                _isCloudConnected.value = true
            }
            response
        } catch (e: Exception) {
            // Localized try/catch: check if physical device has internet
            val hasHardwareInternet = isInternetAvailable(context)
            if (hasHardwareInternet) {
                // The device has valid cellular/WiFi internet; do NOT flag offline
                _isCloudConnected.value = true
            } else {
                _isCloudConnected.value = false
            }
            if (e is IOException) {
                throw e
            } else {
                throw IOException(e.message, e)
            }
        }
    }

    companion object {
        fun isInternetAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        }
    }
}
