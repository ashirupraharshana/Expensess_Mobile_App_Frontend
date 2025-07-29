package com.example.finbot.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.example.finbot.R
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class NetworkErrorHandler private constructor(
    private val context: Context,
    private val container: ViewGroup,
    private val retryCallback: () -> Unit
) {

    companion object {
        fun create(
            context: Context,
            container: ViewGroup,
            retryCallback: () -> Unit
        ): NetworkErrorHandler {
            return NetworkErrorHandler(context, container, retryCallback)
        }
    }

    private var errorView: View? = null
    private var isErrorShown = false
    private var lastUpdatedTime: Long = 0
    private val networkUtils = NetworkUtils.getInstance(context)
    private val dateFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var lifecycleOwner: LifecycleOwner? = null

    // Views
    private lateinit var errorIcon: ImageView
    private lateinit var connectionIndicator: View
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var closeButton: ImageView
    private lateinit var connectionStatusFooter: View
    private lateinit var connectionTypeIcon: ImageView
    private lateinit var connectionStatusText: TextView
    private lateinit var lastUpdatedText: TextView

    fun initialize(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner

        // Observe network changes
        networkUtils.isConnected.observe(lifecycleOwner, Observer { isConnected ->
            if (isConnected == true) {
                // Network is available - hide any error messages
                hideError()
                updateLastUpdatedTime()
            }
            // Don't automatically show error when offline - let the calling code decide
        })

        networkUtils.connectionType.observe(lifecycleOwner, Observer { connectionType ->
            updateConnectionStatus(connectionType)
        })
    }

    // Only show error when explicitly called
    fun showError(error: NetworkUtils.NetworkError, responseCode: Int = -1) {
        // Don't show network error if we're actually connected
        if (error == NetworkUtils.NetworkError.NO_INTERNET && networkUtils.isNetworkAvailable()) {
            return
        }

        if (isErrorShown) return

        createErrorView()
        setupErrorContent(error, responseCode)
        animateErrorIn()
        isErrorShown = true
    }

    fun hideError() {
        if (!isErrorShown || errorView == null) return

        animateErrorOut {
            container.removeView(errorView)
            errorView = null
            isErrorShown = false
        }
    }

    private fun createErrorView() {
        if (errorView != null) return

        val inflater = LayoutInflater.from(context)
        errorView = inflater.inflate(R.layout.network_error_layout, container, false)

        // Initialize views
        errorIcon = errorView!!.findViewById(R.id.errorIcon)
        connectionIndicator = errorView!!.findViewById(R.id.connectionIndicator)
        errorTitle = errorView!!.findViewById(R.id.errorTitle)
        errorMessage = errorView!!.findViewById(R.id.errorMessage)
        retryButton = errorView!!.findViewById(R.id.retryButton)
        settingsButton = errorView!!.findViewById(R.id.settingsButton)
        closeButton = errorView!!.findViewById(R.id.closeErrorButton)
        connectionStatusFooter = errorView!!.findViewById(R.id.connectionStatusFooter)
        connectionTypeIcon = errorView!!.findViewById(R.id.connectionTypeIcon)
        connectionStatusText = errorView!!.findViewById(R.id.connectionStatusText)
        lastUpdatedText = errorView!!.findViewById(R.id.lastUpdatedText)

        setupClickListeners()
        container.addView(errorView, 0) // Add at top
    }

    private fun setupClickListeners() {
        retryButton.setOnClickListener {
            // Show loading state
            retryButton.text = "Retrying..."
            retryButton.isEnabled = false

            // Perform retry
            retryCallback.invoke()

            // Reset button after delay
            retryButton.postDelayed({
                if (::retryButton.isInitialized) {
                    retryButton.text = "Retry"
                    retryButton.isEnabled = true
                }
            }, 2000)
        }

        settingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            context.startActivity(intent)
        }

        closeButton.setOnClickListener {
            hideError()
        }
    }

    private fun setupErrorContent(error: NetworkUtils.NetworkError, responseCode: Int) {
        val title = networkUtils.getErrorTitle(error)
        val message = networkUtils.getErrorMessage(error)

        errorTitle.text = title
        errorMessage.text = message

        // Set appropriate icon and colors based on error type
        when (error) {
            NetworkUtils.NetworkError.NO_INTERNET -> {
                errorIcon.setImageResource(R.drawable.ic_wifi_off)
                connectionIndicator.background = ContextCompat.getDrawable(context, R.drawable.connection_indicator)
                connectionStatusFooter.visibility = View.VISIBLE
                settingsButton.visibility = View.VISIBLE
            }
            NetworkUtils.NetworkError.SERVER_ERROR_404 -> {
                errorIcon.setImageResource(R.drawable.ic_error_outline)
                connectionIndicator.background = ContextCompat.getDrawable(context, R.drawable.connection_indicator)
                connectionStatusFooter.visibility = View.GONE
                settingsButton.visibility = View.GONE

                // Special handling for 404
                errorTitle.text = "Service Not Found (404)"
                errorMessage.text = "The requested service endpoint is not available. This might be a temporary server issue."
            }
            NetworkUtils.NetworkError.SERVER_ERROR_500 -> {
                errorIcon.setImageResource(R.drawable.ic_error_outline)
                connectionIndicator.background = ContextCompat.getDrawable(context, R.drawable.connection_indicator)
                connectionStatusFooter.visibility = View.GONE
                settingsButton.visibility = View.GONE
            }
            else -> {
                errorIcon.setImageResource(R.drawable.ic_error_outline)
                connectionIndicator.background = ContextCompat.getDrawable(context, R.drawable.connection_indicator)
                connectionStatusFooter.visibility = View.GONE
                settingsButton.visibility = View.GONE
            }
        }

        // Add response code to message if available
        if (responseCode > 0 && responseCode != -1) {
            errorMessage.text = "${errorMessage.text}\n\nError Code: $responseCode"
        }
    }

    private fun updateConnectionStatus(connectionType: NetworkUtils.ConnectionType) {
        if (!::connectionStatusText.isInitialized) return

        val typeString = networkUtils.getConnectionTypeString(connectionType)
        val isConnected = connectionType != NetworkUtils.ConnectionType.NONE

        if (isConnected) {
            connectionIndicator.background = ContextCompat.getDrawable(context, R.drawable.connection_indicator_online)
            connectionStatusText.text = "Connected via $typeString"
            connectionTypeIcon.setImageResource(when (connectionType) {
                NetworkUtils.ConnectionType.WIFI -> android.R.drawable.ic_menu_mylocation
                NetworkUtils.ConnectionType.CELLULAR -> android.R.drawable.ic_menu_call
                else -> android.R.drawable.ic_menu_mylocation
            })
        } else {
            connectionIndicator.background = ContextCompat.getDrawable(context, R.drawable.connection_indicator)
            connectionStatusText.text = "Offline - No internet connection"
            connectionTypeIcon.setImageResource(R.drawable.ic_wifi_off)
        }

        updateLastUpdatedDisplay()
    }

    private fun updateLastUpdatedTime() {
        lastUpdatedTime = System.currentTimeMillis()
        updateLastUpdatedDisplay()
    }

    private fun updateLastUpdatedDisplay() {
        if (!::lastUpdatedText.isInitialized) return

        val text = if (lastUpdatedTime > 0) {
            "Last updated: ${dateFormatter.format(Date(lastUpdatedTime))}"
        } else {
            "Last updated: Never"
        }
        lastUpdatedText.text = text
    }

    private fun animateErrorIn() {
        errorView?.let { view ->
            view.alpha = 0f
            view.translationY = -100f
            view.visibility = View.VISIBLE

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Pulse the connection indicator
            pulseConnectionIndicator()
        }
    }

    private fun animateErrorOut(onComplete: () -> Unit) {
        errorView?.let { view ->
            view.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onComplete()
                    }
                })
                .start()
        } ?: onComplete()
    }

    private fun pulseConnectionIndicator() {
        if (!::connectionIndicator.isInitialized) return

        val animator = ValueAnimator.ofFloat(1f, 1.2f, 1f)
        animator.duration = 1000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            connectionIndicator.scaleX = scale
            connectionIndicator.scaleY = scale
        }
        animator.start()
    }

    // Public methods for showing specific errors - only call these when actually needed
    fun showNetworkError() {
        // Only show if actually offline
        if (!networkUtils.isNetworkAvailable()) {
            showError(NetworkUtils.NetworkError.NO_INTERNET)
        }
    }

    fun showServerError(responseCode: Int) {
        val error = networkUtils.getNetworkErrorType(responseCode)
        showError(error, responseCode)
    }

    fun showTimeoutError() = showError(NetworkUtils.NetworkError.TIMEOUT)

    fun showError(exception: Exception) {
        val error = networkUtils.getNetworkErrorFromException(exception)
        showError(error)
    }

    fun cleanup() {
        hideError()
        lifecycleOwner = null
        // Don't unregister network callback here - let NetworkUtils handle its own lifecycle
    }
}