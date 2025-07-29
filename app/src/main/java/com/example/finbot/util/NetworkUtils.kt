package com.example.finbot.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class NetworkUtils private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: NetworkUtils? = null

        fun getInstance(context: Context): NetworkUtils {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkUtils(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected

    private val _connectionType = MutableLiveData<ConnectionType>()
    val connectionType: LiveData<ConnectionType> = _connectionType

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val connectionType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> ConnectionType.WIFI
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> ConnectionType.CELLULAR
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
            _connectionType.postValue(connectionType)
            _isConnected.postValue(true)
        }

        override fun onLost(network: Network) {
            _isConnected.postValue(false)
            _connectionType.postValue(ConnectionType.NONE)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isConnected = hasInternet && hasValidated

            val connectionType = when {
                !isConnected -> ConnectionType.NONE
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }

            _isConnected.postValue(isConnected)
            _connectionType.postValue(connectionType)
        }
    }

    init {
        registerNetworkCallback()
        // Set initial state
        _isConnected.value = isNetworkAvailable()
        _connectionType.value = getCurrentConnectionType()
    }

    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getCurrentConnectionType(): ConnectionType {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.NONE

                when {
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> ConnectionType.NONE
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                    else -> ConnectionType.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> ConnectionType.WIFI
                    ConnectivityManager.TYPE_MOBILE -> ConnectionType.CELLULAR
                    ConnectivityManager.TYPE_ETHERNET -> ConnectionType.ETHERNET
                    else -> if (networkInfo?.isConnected == true) ConnectionType.UNKNOWN else ConnectionType.NONE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ConnectionType.NONE
        }
    }

    fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Connection types
    enum class ConnectionType {
        NONE, WIFI, CELLULAR, ETHERNET, UNKNOWN
    }

    // Network error types
    enum class NetworkError {
        NO_INTERNET,
        SERVER_ERROR_404,
        SERVER_ERROR_500,
        TIMEOUT,
        CONNECTION_REFUSED,
        DNS_RESOLUTION_FAILED,
        UNKNOWN
    }

    fun getNetworkErrorType(responseCode: Int): NetworkError {
        return when (responseCode) {
            404 -> NetworkError.SERVER_ERROR_404
            in 500..599 -> NetworkError.SERVER_ERROR_500
            -1 -> NetworkError.CONNECTION_REFUSED
            else -> NetworkError.UNKNOWN
        }
    }

    fun getNetworkErrorFromException(exception: Exception): NetworkError {
        return when {
            exception.message?.contains("timeout", ignoreCase = true) == true -> NetworkError.TIMEOUT
            exception.message?.contains("refused", ignoreCase = true) == true -> NetworkError.CONNECTION_REFUSED
            exception.message?.contains("nodename", ignoreCase = true) == true -> NetworkError.DNS_RESOLUTION_FAILED
            exception.message?.contains("network", ignoreCase = true) == true -> NetworkError.NO_INTERNET
            else -> NetworkError.UNKNOWN
        }
    }

    fun getErrorMessage(error: NetworkError): String {
        return when (error) {
            NetworkError.NO_INTERNET -> "No internet connection. Please check your network settings and try again."
            NetworkError.SERVER_ERROR_404 -> "The requested service is currently unavailable. Please try again later."
            NetworkError.SERVER_ERROR_500 -> "Server is experiencing issues. Please try again in a few minutes."
            NetworkError.TIMEOUT -> "Connection timeout. Please check your network and try again."
            NetworkError.CONNECTION_REFUSED -> "Unable to connect to server. Please check your internet connection."
            NetworkError.DNS_RESOLUTION_FAILED -> "Unable to resolve server address. Please check your DNS settings."
            NetworkError.UNKNOWN -> "An unexpected error occurred. Please try again."
        }
    }

    fun getErrorTitle(error: NetworkError): String {
        return when (error) {
            NetworkError.NO_INTERNET -> "No Internet Connection"
            NetworkError.SERVER_ERROR_404 -> "Service Unavailable"
            NetworkError.SERVER_ERROR_500 -> "Server Error"
            NetworkError.TIMEOUT -> "Connection Timeout"
            NetworkError.CONNECTION_REFUSED -> "Connection Failed"
            NetworkError.DNS_RESOLUTION_FAILED -> "DNS Error"
            NetworkError.UNKNOWN -> "Connection Error"
        }
    }

    fun getConnectionTypeString(type: ConnectionType): String {
        return when (type) {
            ConnectionType.WIFI -> "WiFi"
            ConnectionType.CELLULAR -> "Mobile Data"
            ConnectionType.ETHERNET -> "Ethernet"
            ConnectionType.UNKNOWN -> "Connected"
            ConnectionType.NONE -> "Offline"
        }
    }
}