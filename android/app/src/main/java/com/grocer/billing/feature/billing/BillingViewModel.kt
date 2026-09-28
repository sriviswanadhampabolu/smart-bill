package com.grocer.billing.feature.billing

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grocer.billing.core.data.remote.NetworkMonitorInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel managing billing and cloud connectivity states.
 * isCloudConnected defaults to true to avoid locking UI in false offline states.
 */
class BillingViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // Initializer defaults to true as specified in requirements
    private val _isCloudConnected = MutableStateFlow(true)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    init {
        startNetworkMonitoring()
        startPeriodicVerification()
    }

    fun isHardwareOnline(): Boolean {
        return NetworkMonitorInterceptor.isInternetAvailable(context)
    }

    private fun checkNetworkCapabilities(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startNetworkMonitoring() {
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
                    _isCloudConnected.value = checkNetworkCapabilities()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _isCloudConnected.value = hasInternet
                }
            })
        } catch (_: Exception) {
            _isCloudConnected.value = checkNetworkCapabilities()
        }
    }

    private fun startPeriodicVerification() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val available = checkNetworkCapabilities()
                _isCloudConnected.value = available
                delay(15_000) // 15-second clean periodic parsing
            }
        }
    }
}
