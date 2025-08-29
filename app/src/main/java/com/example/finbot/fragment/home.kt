package com.example.finbot.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.finbot.R
import com.example.finbot.adapter.ExpenseAdapter
import com.example.finbot.model.Expense
import com.example.finbot.util.NetworkErrorHandler
import com.example.finbot.util.NetworkUtils
import com.example.finbot.util.NotificationHelper
import com.example.finbot.util.SharedPreferencesManager
import com.example.finbot.util.SnackbarUtil
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*
import android.app.PendingIntent
import android.content.Intent
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.DecelerateInterpolator
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.UnknownHostException

class homeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateTextView: TextView
    private lateinit var totalExpenseTextView: TextView
    private lateinit var budgetTextView: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressPercentage: TextView
    private lateinit var spentPercentText: TextView
    private lateinit var limitText: TextView
    private lateinit var welcomeNote: TextView
    private lateinit var sharedPrefsManager: SharedPreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var adapter: ExpenseAdapter
    private lateinit var networkErrorHandler: NetworkErrorHandler
    private lateinit var networkUtils: NetworkUtils

    // Categories used for expense spinner
    private val categories = arrayOf("Food", "Shopping", "Transport", "Health", "Utility", "Other")

    // Currency mapping
    private val currencyMap = mapOf(
        0 to "LKR",
        1 to "USD",
        2 to "EUR",
        3 to "GBP",
        4 to "INR",
        5 to "AUD"
    )

    // Notification tracking
    private val NOTIFICATION_PREF = "budget_notification_tracker"
    private val NOTIFICATION_SENT_KEY = "notification_sent"
    private val NOTIFICATION_MONTH_KEY = "notification_month"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.home, container, false)

        // Initialize welcome TextView
        welcomeNote = view.findViewById(R.id.WelcomeNote)

        // Initialize managers
        sharedPrefsManager = SharedPreferencesManager.getInstance(requireContext())
        notificationHelper = NotificationHelper.getInstance(requireContext())
        networkUtils = NetworkUtils.getInstance(requireContext())

        // Initialize network error handler
        val errorContainer = view.findViewById<ViewGroup>(R.id.networkErrorContainer)
        networkErrorHandler = NetworkErrorHandler.create(requireContext(), errorContainer) {
            // Retry callback
            retryNetworkOperations()
        }

        // Initialize views
        recyclerView = view.findViewById(R.id.expensesRecyclerView)
        emptyStateTextView = view.findViewById(R.id.emptyStateText)
        totalExpenseTextView = view.findViewById(R.id.totalExpenseText)
        budgetTextView = view.findViewById(R.id.budgetText)
        progressBar = view.findViewById(R.id.progressBar)
        progressPercentage = view.findViewById(R.id.progressPercentage)
        spentPercentText = view.findViewById(R.id.spentPercentText)
        limitText = view.findViewById(R.id.limitText)

        // Set up RecyclerView with fixed height issue
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.isNestedScrollingEnabled = true

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize network error handler with lifecycle
        networkErrorHandler.initialize(viewLifecycleOwner)

        // Animate UI elements when fragment is created
        animateUIElements()
    }

    override fun onResume() {
        super.onResume()

        // Always try to load data - individual methods will handle errors appropriately
        loadUsernameFromBackend()
        fetchCurrencyFromBackend()

        // Initialize current displayed value before fetching new data
        if (::totalExpenseTextView.isInitialized) {
            initializeCurrentDisplayedValue()
        }

        loadExpenses()
        updateBudgetInfo()
        fetchAndDisplayTotalExpensesWithAnimation()
    }


    override fun onDestroy() {
        super.onDestroy()
        // Cleanup network resources
        if (::networkErrorHandler.isInitialized) {
            networkErrorHandler.cleanup()
        }
    }

    private fun retryNetworkOperations() {
        // Always attempt retry - methods will handle their own error states
        loadUsernameFromBackend()
        fetchCurrencyFromBackend()
        loadExpenses()
        updateBudgetInfo()
        fetchAndDisplayTotalExpensesWithAnimation()
    }


    private fun getUserIdFromSession(): String {
        val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
        return sharedPref.getString("user_id", "") ?: ""
    }

    private fun fetchCurrencyFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/budget/currency?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val currencyId = try {
                        response.trim().toInt()
                    } catch (e: NumberFormatException) {
                        0
                    }

                    val currencyString = currencyMap[currencyId] ?: "LKR"

                    if (isAdded && context != null) {
                        val currentActivity = activity
                        currentActivity?.runOnUiThread {
                            if (!isAdded || context == null) return@runOnUiThread

                            try {
                                sharedPrefsManager.setCurrency(currencyString)
                                updateBudgetInfo()
                                fetchAndDisplayTotalExpenses()
                            } catch (e: IllegalStateException) {
                                println("Fragment detached during currency update: ${e.message}")
                            }
                        }
                    }
                } else {
                    // Only show server error UI messages, don't show for network issues when online
                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            when (responseCode) {
                                404 -> networkErrorHandler.showServerError(404)
                                in 500..599 -> networkErrorHandler.showServerError(responseCode)
                                // Don't show UI for other HTTP errors when online
                            }
                        }
                    }
                    println("Currency fetch failed with response code: $responseCode")
                }
                connection.disconnect()
            } catch (e: SocketTimeoutException) {
                println("Timeout fetching currency: ${e.message}")
                if (shouldShowNetworkError()) {
                    handleNetworkException(e, "Connection timeout while fetching currency")
                }
            } catch (e: ConnectException) {
                println("Connection failed fetching currency: ${e.message}")
                if (shouldShowNetworkError()) {
                    handleNetworkException(e, "Unable to connect to server")
                }
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                println("Error fetching currency: ${e.message}")
                if (shouldShowNetworkError()) {
                    handleNetworkException(e, "Error fetching currency")
                }
            }
        }
    }


    private fun loadUsernameFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) {
            if (isAdded && ::welcomeNote.isInitialized) {
                welcomeNote.text = "Welcome, User"
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/users/username/$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    val username = response.trim()

                    if (isAdded && context != null) {
                        val currentActivity = activity
                        currentActivity?.runOnUiThread {
                            if (!isAdded || context == null) return@runOnUiThread

                            try {
                                welcomeNote.text = "Welcome, $username"
                                val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
                                sharedPref.edit().putString("username", username).apply()
                            } catch (e: IllegalStateException) {
                                println("Fragment detached during username update: ${e.message}")
                            }
                        }
                    }
                } else {
                    // Handle error responses - show cached data and optionally show server errors
                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            when (responseCode) {
                                404 -> networkErrorHandler.showServerError(404)
                                in 500..599 -> networkErrorHandler.showServerError(responseCode)
                            }

                            // Always fallback to cached username
                            if (::welcomeNote.isInitialized) {
                                val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
                                val cachedUsername = sharedPref.getString("username", "User")
                                welcomeNote.text = "Welcome, $cachedUsername"
                            }
                        }
                    }
                    println("Username fetch failed with response code: $responseCode")
                }
                connection.disconnect()
            } catch (e: SocketTimeoutException) {
                println("Timeout loading username: ${e.message}")
                if (shouldShowNetworkError()) {
                    handleNetworkException(e, "Connection timeout while loading username")
                }
                // Always fallback to cached data
                loadCachedUsername()
            } catch (e: ConnectException) {
                println("Connection failed loading username: ${e.message}")
                if (shouldShowNetworkError()) {
                    handleNetworkException(e, "Unable to connect to server")
                }
                loadCachedUsername()
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
                loadCachedUsername()
            } catch (e: Exception) {
                println("Error loading username: ${e.message}")
                if (shouldShowNetworkError()) {
                    handleNetworkException(e, "Error loading username")
                }
                loadCachedUsername()
            }
        }
    }
    private fun loadCachedUsername() {
        if (isAdded && context != null) {
            activity?.runOnUiThread {
                if (!isAdded || context == null) return@runOnUiThread

                try {
                    if (::welcomeNote.isInitialized) {
                        val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
                        val cachedUsername = sharedPref.getString("username", "User")
                        welcomeNote.text = "Welcome, $cachedUsername"
                    }
                } catch (e: Exception) {
                    println("Error loading cached username: ${e.message}")
                }
            }
        }
    }

    private fun handleNetworkException(exception: Exception, context: String) {
        println("$context: ${exception.message}")
        if (isAdded && this.context != null) {
            activity?.runOnUiThread {
                if (!isAdded || this.context == null) return@runOnUiThread

                // Only show network error UI if actually offline
                when (exception) {
                    is UnknownHostException -> {
                        // Always show for DNS issues
                        networkErrorHandler.showError(exception)
                    }
                    else -> {
                        if (shouldShowNetworkError()) {
                            networkErrorHandler.showError(exception)
                        }
                        // If online, just log the error without showing UI
                    }
                }
            }
        }
    }


    // Enhanced fetchAndDisplayTotalExpenses with error handling
    private fun fetchAndDisplayTotalExpenses() {
        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/expenses/total?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val totalExpenses = response.toDoubleOrNull() ?: 0.0

                    if (isAdded && context != null) {
                        val currentActivity = activity
                        currentActivity?.runOnUiThread {
                            if (!isAdded || context == null || !::totalExpenseTextView.isInitialized) return@runOnUiThread

                            try {
                                totalExpenseTextView.text =
                                    "$currency ${String.format("%.2f", totalExpenses)}"
                            } catch (e: IllegalStateException) {
                                println("Fragment detached during total expenses update: ${e.message}")
                            }
                        }
                    }
                } else {
                    // Handle specific error codes - only show UI errors when appropriate
                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            // Only show server errors, not network errors when online
                            if (responseCode == 404) {
                                networkErrorHandler.showServerError(404)
                            } else if (responseCode >= 500) {
                                networkErrorHandler.showServerError(responseCode)
                            }
                            // Don't show anything for other errors - just log them
                        }
                    }
                }
                connection.disconnect()

            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout while fetching total expenses")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed while fetching expenses")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error fetching total expenses")
            }
        }
    }

    // Enhanced loadExpenses with error handling
    private fun loadExpenses() {
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/expenses/user?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val expenses = mutableListOf<Expense>()
                    val jsonArray = org.json.JSONArray(response)

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)

                        val id = item.getString("id")
                        val name = item.getString("name")
                        val date = item.getString("date")
                        val amount = item.getString("amount")
                        val categoryId = item.getInt("categoryId")
                        val time = if (item.has("time")) item.getString("time") else "00:00"

                        val category = getCategoryFromId(categoryId)
                        val iconResId = getCategoryIconResId(category)

                        val expense =
                            Expense(iconResId, name, category, date, time, amount, categoryId, id)
                        expenses.add(expense)
                    }

                    if (isAdded && context != null) {
                        val currentActivity = activity
                        currentActivity?.runOnUiThread {
                            if (!isAdded || context == null) return@runOnUiThread

                            try {
                                if (expenses.isEmpty()) {
                                    recyclerView.visibility = View.GONE
                                    emptyStateTextView.visibility = View.VISIBLE

                                    // Animate empty state
                                    emptyStateTextView.alpha = 0f
                                    emptyStateTextView.animate()
                                        .alpha(1f)
                                        .setDuration(500)
                                        .start()
                                } else {
                                    recyclerView.visibility = View.VISIBLE
                                    emptyStateTextView.visibility = View.GONE

                                    adapter =
                                        ExpenseAdapter(requireContext(), expenses) { expense ->
                                            showExpenseOptionsDialog(expense)
                                        }
                                    recyclerView.adapter = adapter

                                    // Animate the expense items after a short delay
                                    recyclerView.postDelayed({
                                        animateExpenseItems()
                                    }, 100)
                                }
                            } catch (e: IllegalStateException) {
                                println("Fragment detached during expenses UI update: ${e.message}")
                            }
                        }
                    }

                } else {
                    // Handle specific error codes - only show UI errors when appropriate
                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            // Only show server errors, not network errors when online
                            if (responseCode == 404) {
                                networkErrorHandler.showServerError(404)
                            } else if (responseCode >= 500) {
                                networkErrorHandler.showServerError(responseCode)
                            }
                            // Don't show anything for other errors - just log them
                        }
                    }
                }
                connection.disconnect()

            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout while loading expenses")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed while loading expenses")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error loading expenses")
            }
        }
    }

    // Enhanced updateBudgetInfo with error handling
    private fun updateBudgetInfo() {
        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch total expenses
                val expenseUrl = URL("http://192.168.22.87:8082/api/expenses/total?userId=$userId")
                val expenseConnection = expenseUrl.openConnection() as HttpURLConnection
                expenseConnection.requestMethod = "GET"
                expenseConnection.connectTimeout = 8000
                expenseConnection.readTimeout = 8000

                val expenseResponseCode = expenseConnection.responseCode
                val expenseResponse = if (expenseResponseCode == HttpURLConnection.HTTP_OK) {
                    expenseConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    expenseConnection.disconnect()
                    if (expenseResponseCode == 404) {
                        activity?.runOnUiThread { networkErrorHandler.showServerError(404) }
                    } else if (expenseResponseCode >= 500) {
                        activity?.runOnUiThread { networkErrorHandler.showServerError(expenseResponseCode) }
                    }
                    return@launch
                }

                val totalExpenses = expenseResponse.toDoubleOrNull() ?: 0.0

                // Fetch budget from backend
                val budgetUrl = URL("http://192.168.22.87:8082/api/budget/get?userId=$userId")
                val budgetConnection = budgetUrl.openConnection() as HttpURLConnection
                budgetConnection.requestMethod = "GET"
                budgetConnection.connectTimeout = 8000
                budgetConnection.readTimeout = 8000

                val responseCode = budgetConnection.responseCode
                val budgetResponse = if (responseCode == HttpURLConnection.HTTP_OK) {
                    budgetConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorResponse = budgetConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    budgetConnection.disconnect()
                    expenseConnection.disconnect()

                    if (responseCode == 404) {
                        activity?.runOnUiThread { networkErrorHandler.showServerError(404) }
                    } else if (responseCode >= 500) {
                        activity?.runOnUiThread { networkErrorHandler.showServerError(responseCode) }
                    }
                    return@launch
                }

                // Parse budget
                val budget = if (budgetResponse.isNullOrEmpty()) {
                    0.0
                } else {
                    try {
                        val budgetJson = JSONObject(budgetResponse)
                        budgetJson.optDouble("budget", 0.0)
                    } catch (jsonException: Exception) {
                        try {
                            budgetResponse.trim().toDoubleOrNull() ?: 0.0
                        } catch (numberException: Exception) {
                            if (budgetResponse.contains("error", ignoreCase = true)) {
                                println("Budget API error: $budgetResponse")
                                0.0
                            } else {
                                0.0
                            }
                        }
                    }
                }

                val percentUsed = if (budget > 0) ((totalExpenses / budget) * 100).toInt() else 0

                // Check if fragment is still attached before updating UI
                if (isAdded && context != null) {
                    val currentActivity = activity
                    if (currentActivity != null) {
                        currentActivity.runOnUiThread {
                            // Double-check fragment is still attached
                            if (!isAdded || context == null) return@runOnUiThread

                            // Handle notifications
                            val notificationsEnabled = sharedPrefsManager.areNotificationsEnabled()
                            if (notificationsEnabled && budget > 0) {
                                // Check if we should reset the exceeded notification flag
                                checkAndResetExceededNotification(percentUsed)

                                // Handle budget notifications
                                handleBudgetNotifications(
                                    percentUsed,
                                    totalExpenses,
                                    budget,
                                    currency
                                )
                            }

                            // Update UI only if fragment is still attached
                            try {
                                totalExpenseTextView.text =
                                    "$currency ${String.format("%.2f", totalExpenses)}"
                                budgetTextView.text = "Budget: $currency ${
                                    String.format(
                                        "%.2f",
                                        budget
                                    )
                                } ($percentUsed% used)"
                                progressBar.progress = percentUsed
                                progressPercentage.text = "$percentUsed%"
                                spentPercentText.text = "Spent: $percentUsed%"
                                limitText.text =
                                    "of $currency ${String.format("%.2f", budget)} limit"

                                // Enhanced color changes with more granular states
                                val context = requireContext()
                                when {
                                    percentUsed >= 100 -> {
                                        budgetTextView.setTextColor(context.getColor(R.color.transport))
                                        progressBar.setIndicatorColor(context.getColor(R.color.transport))
                                        limitText.setTextColor(context.getColor(R.color.transport))
                                        progressPercentage.setTextColor(context.getColor(R.color.white))
                                    }

                                    percentUsed >= 90 -> {
                                        budgetTextView.setTextColor(context.getColor(R.color.transport))
                                        progressBar.setIndicatorColor(context.getColor(R.color.transport))
                                        limitText.setTextColor(context.getColor(R.color.transport))
                                        progressPercentage.setTextColor(context.getColor(R.color.white))
                                    }

                                    percentUsed >= 75 -> {
                                        budgetTextView.setTextColor(context.getColor(R.color.food))
                                        progressBar.setIndicatorColor(context.getColor(R.color.food))
                                        limitText.setTextColor(context.getColor(R.color.food))
                                    }

                                    else -> {
                                        budgetTextView.setTextColor(context.getColor(R.color.food))
                                        progressBar.setIndicatorColor(context.getColor(R.color.Blue))
                                        limitText.setTextColor(context.getColor(R.color.Blue))
                                        progressPercentage.setTextColor(context.getColor(R.color.black))
                                    }
                                }
                            } catch (e: IllegalStateException) {
                                // Fragment is no longer attached, ignore UI updates
                                println("Fragment detached during UI update: ${e.message}")
                            }
                        }
                    }
                }

                // Close connections
                expenseConnection.disconnect()
                budgetConnection.disconnect()

            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout while updating budget info")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed while updating budget")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error updating budget info")

                // Set default values in case of error
                if (isAdded && context != null) {
                    activity?.runOnUiThread {
                        if (!isAdded || context == null) return@runOnUiThread

                        try {
                            val currency = sharedPrefsManager.getCurrency()
                            budgetTextView.text = "Budget: $currency 0.00 (0% used)"
                            progressBar.progress = 0
                            progressPercentage.text = "0%"
                            spentPercentText.text = "Spent: 0%"
                            limitText.text = "of $currency 0.00 limit"
                        } catch (ex: IllegalStateException) {
                            // Fragment is no longer attached, ignore
                            println("Fragment detached during error handling: ${ex.message}")
                        }
                    }
                }
            }
        }
    }

    private fun handleBudgetNotifications(
        percentUsed: Int,
        totalExpenses: Double,
        budget: Double,
        currency: String
    ) {
        val alertPercent = sharedPrefsManager.getBudgetAlertPercent()

        when {
            percentUsed >= 100 -> {
                // Show exceeded notification only once per month when limit is first exceeded
                showBudgetExceededNotificationOnce(totalExpenses, budget, currency, percentUsed)
            }

            percentUsed >= alertPercent -> {
                showBudgetNotificationOnce(
                    title = "Budget Alert",
                    message = "You have used $percentUsed% of your monthly budget ($currency ${
                        String.format(
                            "%.2f",
                            totalExpenses
                        )
                    } of $currency ${String.format("%.2f", budget)})",
                    notificationType = "alert"
                )
            }

            percentUsed >= 90 -> {
                showBudgetNotificationOnce(
                    title = "Budget Warning",
                    message = "You're approaching your budget limit! $percentUsed% used ($currency ${
                        String.format(
                            "%.2f",
                            totalExpenses
                        )
                    } of $currency ${String.format("%.2f", budget)})",
                    notificationType = "warning"
                )
            }
        }
    }

    private fun showBudgetNotificationOnce(
        title: String,
        message: String,
        notificationType: String
    ) {
        val notificationPrefs =
            requireContext().getSharedPreferences(NOTIFICATION_PREF, Context.MODE_PRIVATE)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonthYear = "$currentYear-$currentMonth"

        val notificationKey = "${notificationType}_$currentMonthYear"
        val notificationSent = notificationPrefs.getBoolean(notificationKey, false)

        // Show notification if not sent for this type this month
        if (!notificationSent) {
            val notificationId = when (notificationType) {
                "alert" -> 1002
                "warning" -> 1003
                else -> 1000
            }

            showNotification(title, message, notificationId)

            // Mark this notification type as sent for this month
            val editor = notificationPrefs.edit()
            editor.putBoolean(notificationKey, true)
            editor.apply()
        }
    }


    private fun showBudgetExceededNotificationOnce(
        totalExpenses: Double,
        budget: Double,
        currency: String,
        percentUsed: Int
    ) {
        val notificationPrefs =
            requireContext().getSharedPreferences(NOTIFICATION_PREF, Context.MODE_PRIVATE)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonthYear = "$currentYear-$currentMonth"

        // Create a unique key for exceeded notification
        val exceededKey = "exceeded_$currentMonthYear"
        val exceededNotificationSent = notificationPrefs.getBoolean(exceededKey, false)

        // Also track the last exceeded percentage to detect new exceeding events
        val lastExceededPercent =
            notificationPrefs.getInt("last_exceeded_percent_$currentMonthYear", 0)

        // Show notification only if:
        // 1. No exceeded notification sent this month, OR
        // 2. The percentage significantly increased (e.g., went from 100% to 110%)
        val shouldShowNotification = !exceededNotificationSent ||
                (percentUsed > lastExceededPercent && percentUsed - lastExceededPercent >= 10)

        if (shouldShowNotification) {
            val exceededBy = percentUsed - 100
            val title = "Budget Exceeded!"
            val message = if (exceededBy > 0) {
                "You have exceeded your monthly budget by $exceededBy%! Current spending: $currency ${
                    String.format(
                        "%.2f",
                        totalExpenses
                    )
                }"
            } else {
                "You have reached your monthly budget limit! Current spending: $currency ${
                    String.format(
                        "%.2f",
                        totalExpenses
                    )
                }"
            }

            showNotification(title, message, 1001)

            // Mark exceeded notification as sent and update the percentage
            val editor = notificationPrefs.edit()
            editor.putBoolean(exceededKey, true)
            editor.putInt("last_exceeded_percent_$currentMonthYear", percentUsed)
            editor.apply()

            println("Budget exceeded notification sent for $percentUsed% usage") // Debug log
        } else {
            println("Budget exceeded notification already sent this month or percentage not significantly changed") // Debug log
        }
    }

    private fun checkAndResetExceededNotification(percentUsed: Int) {
        if (percentUsed < 100) {
            val notificationPrefs =
                requireContext().getSharedPreferences(NOTIFICATION_PREF, Context.MODE_PRIVATE)
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val currentMonthYear = "$currentYear-$currentMonth"

            val editor = notificationPrefs.edit()
            editor.putBoolean("exceeded_$currentMonthYear", false)
            editor.putInt("last_exceeded_percent_$currentMonthYear", 0)
            editor.apply()

            println("Reset exceeded notification flag - budget back under 100%") // Debug log
        }
    }

    private fun showNotification(title: String, message: String, notificationId: Int = 1000) {
        try {
            val notificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelId = "budget_alert_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "Notifications when budget limit is exceeded"
                channel.enableVibration(true)
                channel.enableLights(true)
                notificationManager.createNotificationChannel(channel)
            }

            // Create an intent to open the app when notification is clicked
            val intent = Intent(requireContext(), requireActivity()::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val pendingIntent = PendingIntent.getActivity(
                requireContext(),
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(requireContext(), channelId)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(notificationId, builder.build())

            println("Budget notification sent: $title - $message") // Debug log

        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to send notification: ${e.message}")
        }
    }

    private fun resetAllNotificationTracking() {
        val notificationPrefs =
            requireContext().getSharedPreferences(NOTIFICATION_PREF, Context.MODE_PRIVATE)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonthYear = "$currentYear-$currentMonth"

        val editor = notificationPrefs.edit()
        editor.putBoolean("alert_$currentMonthYear", false)
        editor.putBoolean("warning_$currentMonthYear", false)
        editor.apply()
    }


    private fun showExpenseOptionsDialog(expense: Expense) {
        val options = arrayOf("Edit", "Delete")

        AlertDialog.Builder(requireContext())
            .setTitle("Expense Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editExpense(expense)
                    1 -> deleteExpense(expense)
                }
            }
            .show()
    }

    private fun editExpense(expense: Expense) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.fragment_add_expense, null)

        dialogView.findViewById<TextView>(R.id.addExpenseTitle).visibility = View.GONE
        dialogView.findViewById<LinearLayout>(R.id.addExpenseButtonGroup).visibility = View.GONE
        val nameInput = dialogView.findViewById<EditText>(R.id.expenseNameInput)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.categorySpinner)
        val dateInput = dialogView.findViewById<TextView>(R.id.dateInput)
        val amountInput = dialogView.findViewById<EditText>(R.id.amountInput)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        categorySpinner.adapter = adapter

        nameInput.setText(expense.name)
        val categoryIndex = categories.indexOf(expense.category)
        if (categoryIndex >= 0) categorySpinner.setSelection(categoryIndex)
        dateInput.text = expense.date
        amountInput.setText(expense.amount)

        // Updated for dark mode compatibility
        dateInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        dateInput.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                R.color.card_background
            )
        )
        amountInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))

        val calendar = Calendar.getInstance()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            val date = dateFormatter.parse(expense.date)
            if (date != null) calendar.time = date
        } catch (e: Exception) {
        }

        dateInput.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, y, m, d ->
                calendar.set(y, m, d)
                dateInput.text = dateFormatter.format(calendar.time)
            }, year, month, day).show()
        }

        AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Edit Expense")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = nameInput.text.toString().trim()
                val category = categorySpinner.selectedItem.toString()
                val date = dateInput.text.toString()
                val amount = amountInput.text.toString().trim()

                if (name.isNotEmpty() && amount.isNotEmpty()) {
                    val iconResId = getCategoryIconResId(category)
                    val categoryId = getCategoryId(category)

                    val updatedExpense = Expense(
                        iconResId,
                        name,
                        category,
                        date,
                        expense.time,
                        amount,
                        categoryId,
                        expense.id
                    )

                    updateExpenseOnServer(updatedExpense)
                } else {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateExpenseOnServer(updatedExpense: Expense) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/expenses/update/${updatedExpense.id}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val jsonObject = org.json.JSONObject().apply {
                    put("name", updatedExpense.name)
                    put("categoryId", updatedExpense.categoryId)
                    put("date", updatedExpense.date)
                    put("time", updatedExpense.time)
                    put("amount", updatedExpense.amount)
                }

                val outputStream = connection.outputStream
                outputStream.write(jsonObject.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode

                requireActivity().runOnUiThread {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val snackbar = Snackbar.make(
                            requireView(),
                            "Expense updated successfully",
                            3000
                        )
                        snackbar.setBackgroundTint(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.snackbar_background_light
                            )
                        )
                        snackbar.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.snackbar_text_light
                            )
                        )
                        snackbar.show()

                        loadExpenses()
                        updateBudgetInfo()
                        animateAfterUpdate() // Use animated version instead of fetchAndDisplayTotalExpenses()

                        try {
                            notificationHelper.checkAndShowBudgetAlertIfNeeded()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        if (responseCode == 404) {
                            networkErrorHandler.showServerError(404)
                        } else if (responseCode >= 500) {
                            networkErrorHandler.showServerError(responseCode)
                        } else {
                            val snackbar = Snackbar.make(
                                requireView(),
                                "Failed to update expense",
                                Snackbar.LENGTH_SHORT
                            )
                            snackbar.setBackgroundTint(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.snackbar_background_light
                                )
                            )
                            snackbar.setTextColor(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.snackbar_text_light
                                )
                            )
                            snackbar.show()
                        }
                    }
                }

                connection.disconnect()

            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout while updating expense")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed while updating expense")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error updating expense")
            }
        }
    }

    private fun getCategoryIconResId(category: String): Int {
        return when (category) {
            "Food" -> R.drawable.food
            "Shopping" -> R.drawable.shopping
            "Transport" -> R.drawable.transport
            "Health" -> R.drawable.health
            "Utility" -> R.drawable.utility
            else -> R.drawable.other
        }
    }

    private fun getCategoryId(category: String): Int {
        return when (category) {
            "Food" -> 1
            "Shopping" -> 2
            "Transport" -> 3
            "Health" -> 4
            "Utility" -> 5
            else -> 6
        }
    }

    private fun getCategoryFromId(categoryId: Int): String {
        return when (categoryId) {
            1 -> "Food"
            2 -> "Shopping"
            3 -> "Transport"
            4 -> "Health"
            5 -> "Utility"
            else -> "Other"
        }
    }

    private fun deleteExpense(expense: Expense) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete this expense?")
            .setPositiveButton("Delete") { _, _ ->
                deleteExpenseFromServer(expense)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExpenseFromServer(expense: Expense) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/expenses/${expense.id}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "DELETE"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode

                requireActivity().runOnUiThread {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val snackbar = Snackbar.make(requireView(), "Expense deleted", 2500)
                        snackbar.setBackgroundTint(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.snackbar_background_light
                            )
                        )
                        snackbar.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.snackbar_text_light
                            )
                        )
                        snackbar.show()

                        loadExpenses()
                        updateBudgetInfo()
                        animateAfterDelete() // Use animated version instead of fetchAndDisplayTotalExpenses()

                        try {
                            notificationHelper.checkAndShowBudgetAlertIfNeeded()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        if (responseCode == 404) {
                            networkErrorHandler.showServerError(404)
                        } else if (responseCode >= 500) {
                            networkErrorHandler.showServerError(responseCode)
                        } else {
                            val snackbar = Snackbar.make(
                                requireView(),
                                "Failed to delete expense",
                                Snackbar.LENGTH_SHORT
                            )
                            val snackbarView = snackbar.view
                            snackbarView.background = ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.snackbar_background
                            )
                            val textView =
                                snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                            textView.setTextColor(Color.WHITE)
                            snackbar.show()
                        }
                    }
                }

                connection.disconnect()

            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout while deleting expense")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed while deleting expense")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error deleting expense")
            }
        }
    }

    private fun animateUIElements() {
        // Welcome card animation
        val welcomeCard = view?.findViewById<androidx.cardview.widget.CardView>(R.id.welcomeCard)
        welcomeCard?.apply {
            alpha = 0f
            translationY = -100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        // Progress bar animation
        progressBar?.apply {
            alpha = 0f
            scaleX = 0f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .setDuration(600)
                .setStartDelay(200)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }

        // Categories animation
        val categoriesContainer = view?.findViewById<LinearLayout>(R.id.categoriesContainer)
        categoriesContainer?.apply {
            alpha = 0f
            translationX = 100f
            animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(400)
                .setStartDelay(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        // RecyclerView animation
        recyclerView?.apply {
            alpha = 0f
            translationY = 50f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        // Animate each category item individually for a staggered effect
        animateCategoryItems()
    }

    private fun animateCategoryItems() {
        val categoriesContainer = view?.findViewById<LinearLayout>(R.id.categoriesContainer)
        categoriesContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val categoryItem = container.getChildAt(i)
                categoryItem.alpha = 0f
                categoryItem.translationY = 30f
                categoryItem.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(500 + (i * 100L)) // Staggered animation
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }
    }

    // Add this method to animate individual expense items when they're loaded
    private fun animateExpenseItems() {
        recyclerView?.apply {
            // Animate items with a staggered effect
            val layoutManager = layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            layoutManager?.let { lm ->
                for (i in 0 until (adapter?.itemCount ?: 0)) {
                    val viewHolder = findViewHolderForAdapterPosition(i)
                    viewHolder?.itemView?.apply {
                        alpha = 0f
                        translationX = 100f
                        animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(300)
                            .setStartDelay(i * 50L)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
                }
            }
        }
    }

    enum class AnimationType {
        ADD, UPDATE, DELETE, DEFAULT
    }

    private var currentDisplayedValue: Double = 0.0

    private fun extractValueFromText(text: String): Double {
        return try {
            // Remove currency and extract number
            val numberPart = text.replace(Regex("[^0-9.]"), "")
            numberPart.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun initializeCurrentDisplayedValue() {
        try {
            val currentText = totalExpenseTextView.text.toString()
            currentDisplayedValue = extractValueFromText(currentText)
        } catch (e: Exception) {
            currentDisplayedValue = 0.0
        }
    }

    // Enhanced fetchAndDisplayTotalExpenses method with rolling animation
    private fun fetchAndDisplayTotalExpensesWithAnimation() {
        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/expenses/total?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val totalExpenses = response.toDoubleOrNull() ?: 0.0

                    if (isAdded && context != null) {
                        val currentActivity = activity
                        currentActivity?.runOnUiThread {
                            if (!isAdded || context == null || !::totalExpenseTextView.isInitialized) return@runOnUiThread

                            try {
                                // Only animate if the value actually changed
                                if (Math.abs(totalExpenses - currentDisplayedValue) > 0.01) {
                                    // Use current displayed value as starting point
                                    animateValueChange(
                                        totalExpenseTextView,
                                        currentDisplayedValue,
                                        totalExpenses,
                                        currency,
                                        AnimationType.DEFAULT
                                    )
                                    // Update tracked value
                                    currentDisplayedValue = totalExpenses
                                } else {
                                    // Value hasn't changed significantly, just update text
                                    totalExpenseTextView.text =
                                        "$currency ${String.format("%.2f", totalExpenses)}"
                                }

                            } catch (e: IllegalStateException) {
                                println("Fragment detached during total expenses update: ${e.message}")
                            }
                        }
                    }
                } else {
                    // Handle specific error codes - only show UI errors when appropriate
                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            // Only show server errors, not network errors when online
                            if (responseCode == 404) {
                                networkErrorHandler.showServerError(404)
                            } else if (responseCode >= 500) {
                                networkErrorHandler.showServerError(responseCode)
                            }
                            // Don't show anything for other errors - just log them
                        }
                    }
                }
                connection.disconnect()

            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout while fetching total expenses with animation")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed while fetching expenses")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error fetching total expenses with animation")
            }
        }
    }

    private fun showUpdateFeedback() {
        try {
            // Gentle pulse effect for updates
            totalExpenseTextView.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(150)
                .withEndAction {
                    totalExpenseTextView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDeleteFeedback() {
        try {
            // Shrink effect to indicate removal
            totalExpenseTextView.animate()
                .alpha(0.6f)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(150)
                .withEndAction {
                    totalExpenseTextView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateAfterUpdate() {
        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/expenses/total?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val totalExpenses = response.toDoubleOrNull() ?: 0.0

                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            if (!isAdded || context == null || !::totalExpenseTextView.isInitialized) return@runOnUiThread

                            try {
                                if (Math.abs(totalExpenses - currentDisplayedValue) > 0.01) {
                                    // Show visual feedback before animation
                                    showUpdateFeedback()

                                    // Delay the animation slightly for better visual effect
                                    totalExpenseTextView.postDelayed({
                                        if (isAdded && context != null) {
                                            animateValueChange(
                                                totalExpenseTextView,
                                                currentDisplayedValue,
                                                totalExpenses,
                                                currency,
                                                AnimationType.UPDATE
                                            )
                                            currentDisplayedValue = totalExpenses
                                        }
                                    }, 300)
                                } else {
                                    // Small change, just update without animation
                                    totalExpenseTextView.text =
                                        "$currency ${String.format("%.2f", totalExpenses)}"
                                    currentDisplayedValue = totalExpenses
                                }
                            } catch (e: IllegalStateException) {
                                println("Fragment detached during update animation: ${e.message}")
                            }
                        }
                    }
                } else {
                    // Handle specific error codes - only show UI errors when appropriate
                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            // Only show server errors, not network errors when online
                            if (responseCode == 404) {
                                networkErrorHandler.showServerError(404)
                            } else if (responseCode >= 500) {
                                networkErrorHandler.showServerError(responseCode)
                            }
                            // Don't show anything for other errors - just log them
                        }
                    }
                }
                connection.disconnect()
            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout during update animation")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed during update")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error during update animation")
            }
        }
    }

    private fun animateValueChange(
        textView: TextView,
        fromValue: Double,
        toValue: Double,
        currency: String,
        animationType: AnimationType
    ) {
        val animator = ValueAnimator.ofFloat(fromValue.toFloat(), toValue.toFloat())
        animator.duration = when (animationType) {
            AnimationType.ADD -> 800L
            AnimationType.DELETE -> 600L
            AnimationType.UPDATE -> 500L
            AnimationType.DEFAULT -> 400L
        }

        animator.interpolator = DecelerateInterpolator()

        // Set text color based on animation type
        val animationColor = getAnimationColor(animationType)
        val originalColor = textView.currentTextColor

        animator.addUpdateListener { valueAnimator ->
            val animatedValue = valueAnimator.animatedValue as Float
            textView.text = "$currency ${String.format("%.2f", animatedValue)}"
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                // Change color at start for visual feedback
                textView.setTextColor(animationColor)
            }

            override fun onAnimationEnd(animation: Animator) {
                // Restore original color
                textView.setTextColor(originalColor)
                // Ensure final value is exact
                textView.text = "$currency ${String.format("%.2f", toValue)}"
            }
        })

        animator.start()
    }

    private fun animateAfterDelete() {
        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.22.87:8082/api/expenses/total?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val totalExpenses = response.toDoubleOrNull() ?: 0.0

                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            if (!isAdded || context == null || !::totalExpenseTextView.isInitialized) return@runOnUiThread

                            try {
                                if (Math.abs(totalExpenses - currentDisplayedValue) > 0.01) {
                                    // Show visual feedback before animation
                                    showDeleteFeedback()

                                    // Delay the animation slightly for dramatic effect
                                    totalExpenseTextView.postDelayed({
                                        if (isAdded && context != null) {
                                            animateValueChange(
                                                totalExpenseTextView,
                                                currentDisplayedValue,
                                                totalExpenses,
                                                currency,
                                                AnimationType.DELETE
                                            )
                                            currentDisplayedValue = totalExpenses
                                        }
                                    }, 400) // Slightly longer delay for delete
                                } else {
                                    // Small change, just update without animation
                                    totalExpenseTextView.text =
                                        "$currency ${String.format("%.2f", totalExpenses)}"
                                    currentDisplayedValue = totalExpenses
                                }
                            } catch (e: IllegalStateException) {
                                println("Fragment detached during delete animation: ${e.message}")
                            }
                        }
                    }
                } else {
                    // Handle specific error codes - only show UI errors when appropriate
                    if (isAdded && context != null) {
                        activity?.runOnUiThread {
                            // Only show server errors, not network errors when online
                            if (responseCode == 404) {
                                networkErrorHandler.showServerError(404)
                            } else if (responseCode >= 500) {
                                networkErrorHandler.showServerError(responseCode)
                            }
                            // Don't show anything for other errors - just log them
                        }
                    }
                }
                connection.disconnect()
            } catch (e: SocketTimeoutException) {
                handleNetworkException(e, "Timeout during delete animation")
            } catch (e: ConnectException) {
                handleNetworkException(e, "Connection failed during delete")
            } catch (e: UnknownHostException) {
                handleNetworkException(e, "Network unavailable")
            } catch (e: Exception) {
                handleNetworkException(e, "Error during delete animation")
            }
        }
    }

    private fun shouldShowNetworkError(): Boolean {
        return !networkUtils.isNetworkAvailable()
    }

    private fun getAnimationColor(animationType: AnimationType): Int {
        return when (animationType) {
            AnimationType.DELETE -> ContextCompat.getColor(
                requireContext(),
                R.color.transport
            ) // Red
            AnimationType.ADD -> ContextCompat.getColor(requireContext(), R.color.food) // Green
            AnimationType.UPDATE -> ContextCompat.getColor(requireContext(), R.color.Blue) // Blue
            AnimationType.DEFAULT -> ContextCompat.getColor(
                requireContext(),
                R.color.text_primary
            ) // Default
        }
    }
}