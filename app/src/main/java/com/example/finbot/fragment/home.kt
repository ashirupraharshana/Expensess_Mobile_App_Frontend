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

class homeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateTextView: TextView
    private lateinit var totalExpenseTextView: TextView
    private lateinit var budgetTextView: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressPercentage: TextView
    private lateinit var spentPercentText: TextView
    private lateinit var limitText: TextView
    private lateinit var welcomeNote: TextView // Add this line
    private lateinit var sharedPrefsManager: SharedPreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var adapter: ExpenseAdapter

    // Categories used for expense spinner
    private val categories = arrayOf("Food", "Shopping", "Transport", "Health", "Utility", "Other")

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

    override fun onResume() {
        super.onResume()
        loadUsernameFromBackend() // Load username from backend
        loadExpenses()
        updateBudgetInfo()
        fetchAndDisplayTotalExpenses()
    }

    private fun getUserIdFromSession(): String {
        val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
        return sharedPref.getString("user_id", "") ?: ""
    }

    // Add this new method to fetch username from backend
    private fun loadUsernameFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) {
            welcomeNote.text = "Welcome, User"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/users/username/$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    // Backend returns plain string, not JSON
                    val username = response.trim()

                    requireActivity().runOnUiThread {
                        welcomeNote.text = "Welcome, $username"
                        // Also update SharedPreferences for future use
                        val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
                        sharedPref.edit().putString("username", username).apply()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        welcomeNote.text = "Welcome, User"
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    // Fallback to SharedPreferences if network fails
                    val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
                    val username = sharedPref.getString("username", "User")
                    welcomeNote.text = "Welcome, $username"
                }
            }
        }
    }

    private fun fetchAndDisplayTotalExpenses() {
        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/expenses/total?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val totalExpenses = response.toDoubleOrNull() ?: 0.0

                    requireActivity().runOnUiThread {
                        totalExpenseTextView.text = "$currency ${String.format("%.2f", totalExpenses)}"
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to fetch total", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadExpenses() {
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/expenses/user?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

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

                        val expense = Expense(iconResId, name, category, date, time, amount, categoryId, id)
                        expenses.add(expense)
                    }

                    requireActivity().runOnUiThread {
                        if (expenses.isEmpty()) {
                            recyclerView.visibility = View.GONE
                            emptyStateTextView.visibility = View.VISIBLE
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            emptyStateTextView.visibility = View.GONE

                            adapter = ExpenseAdapter(requireContext(), expenses) { expense ->
                                showExpenseOptionsDialog(expense)
                            }
                            recyclerView.adapter = adapter
                        }
                    }

                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to load expenses", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateBudgetInfo() {
        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch total expenses
                val expenseUrl = URL("http://192.168.1.100:8082/api/expenses/total?userId=$userId")
                val expenseConnection = expenseUrl.openConnection() as HttpURLConnection
                expenseConnection.requestMethod = "GET"

                val expenseResponse = expenseConnection.inputStream.bufferedReader().use { it.readText() }
                val totalExpenses = expenseResponse.toDoubleOrNull() ?: 0.0

                // Fetch budget from backend
                val budgetUrl = URL("http://192.168.1.100:8082/api/budget/get?userId=$userId")
                val budgetConnection = budgetUrl.openConnection() as HttpURLConnection
                budgetConnection.requestMethod = "GET"

                val responseCode = budgetConnection.responseCode
                val budgetResponse = if (responseCode == HttpURLConnection.HTTP_OK) {
                    budgetConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    budgetConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                // Check if response is empty or null
                val budget = if (budgetResponse.isNullOrEmpty()) {
                    0.0
                } else {
                    try {
                        // Try to parse as JSON first
                        val budgetJson = JSONObject(budgetResponse)
                        budgetJson.optDouble("budget", 0.0)
                    } catch (jsonException: Exception) {
                        // If JSON parsing fails, try to parse as plain number
                        try {
                            budgetResponse.trim().toDoubleOrNull() ?: 0.0
                        } catch (numberException: Exception) {
                            // If both fail, check if it's an error message
                            if (budgetResponse.contains("error", ignoreCase = true)) {
                                // Log error and use default budget
                                println("Budget API error: $budgetResponse")
                                0.0
                            } else {
                                0.0
                            }
                        }
                    }
                }

                val percentUsed = if (budget > 0) ((totalExpenses / budget) * 100).toInt() else 0

                // Trigger notification if budget exceeded based on alert percent
                val alertPercent = sharedPrefsManager.getBudgetAlertPercent()
                val notificationsEnabled = sharedPrefsManager.areNotificationsEnabled()

                if (notificationsEnabled && percentUsed >= alertPercent) {
                    requireActivity().runOnUiThread {
                        showNotificationOnce(
                            title = "Budget Alert",
                            message = "You have exceeded $alertPercent% of your monthly budget!"
                        )
                    }
                }

                requireActivity().runOnUiThread {
                    // Update UI
                    totalExpenseTextView.text = "$currency ${String.format("%.2f", totalExpenses)}"
                    budgetTextView.text = "Budget: $currency ${String.format("%.2f", budget)} ($percentUsed% used)"
                    progressBar.progress = percentUsed
                    progressPercentage.text = "$percentUsed%"
                    spentPercentText.text = "Spent: $percentUsed%"
                    limitText.text = "of $currency ${String.format("%.2f", budget)} limit"

                    // Color changes
                    when {
                        percentUsed >= 100 -> {
                            budgetTextView.setTextColor(requireContext().getColor(R.color.transport))
                            progressBar.setIndicatorColor(requireContext().getColor(R.color.transport))
                            limitText.setTextColor(requireContext().getColor(R.color.transport))
                        }
                        percentUsed > 90 -> {
                            budgetTextView.setTextColor(requireContext().getColor(R.color.transport))
                            progressBar.setIndicatorColor(requireContext().getColor(R.color.transport))
                            limitText.setTextColor(requireContext().getColor(R.color.transport))
                        }
                        percentUsed > 75 -> {
                            budgetTextView.setTextColor(requireContext().getColor(R.color.food))
                            progressBar.setIndicatorColor(requireContext().getColor(R.color.food))
                        }
                        else -> {
                            budgetTextView.setTextColor(requireContext().getColor(R.color.food))
                            progressBar.setIndicatorColor(requireContext().getColor(R.color.Blue))
                        }
                    }
                }

                // Close connections
                expenseConnection.disconnect()
                budgetConnection.disconnect()

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error loading budget: ${e.message}", Toast.LENGTH_SHORT).show()

                    // Set default values in case of error
                    val currency = sharedPrefsManager.getCurrency()
                    budgetTextView.text = "Budget: $currency 0.00 (0% used)"
                    progressBar.progress = 0
                    progressPercentage.text = "0%"
                    spentPercentText.text = "Spent: 0%"
                    limitText.text = "of $currency 0.00 limit"
                }
            }
        }
    }

    private fun showNotificationOnce(title: String, message: String) {
        val notificationPrefs = requireContext().getSharedPreferences(NOTIFICATION_PREF, Context.MODE_PRIVATE)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonthYear = "$currentYear-$currentMonth"

        val lastNotificationMonth = notificationPrefs.getString(NOTIFICATION_MONTH_KEY, "")
        val notificationSent = notificationPrefs.getBoolean(NOTIFICATION_SENT_KEY, false)

        // Check if we're in a new month or if notification hasn't been sent this month
        if (lastNotificationMonth != currentMonthYear || !notificationSent) {
            // Show the notification
            showNotification(title, message)

            // Mark notification as sent for this month
            val editor = notificationPrefs.edit()
            editor.putBoolean(NOTIFICATION_SENT_KEY, true)
            editor.putString(NOTIFICATION_MONTH_KEY, currentMonthYear)
            editor.apply()
        }
    }

    private fun showNotification(title: String, message: String) {
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
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.food)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(Random().nextInt(), builder.build())
    }

    /**
     * Reset notification flag - call this when budget is updated or when you want to allow
     * notifications again for the current period
     */
    private fun resetNotificationFlag() {
        val notificationPrefs = requireContext().getSharedPreferences(NOTIFICATION_PREF, Context.MODE_PRIVATE)
        val editor = notificationPrefs.edit()
        editor.putBoolean(NOTIFICATION_SENT_KEY, false)
        editor.apply()
    }

    /**
     * Check if notification has been sent this month
     */
    private fun hasNotificationBeenSent(): Boolean {
        val notificationPrefs = requireContext().getSharedPreferences(NOTIFICATION_PREF, Context.MODE_PRIVATE)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonthYear = "$currentYear-$currentMonth"

        val lastNotificationMonth = notificationPrefs.getString(NOTIFICATION_MONTH_KEY, "")
        val notificationSent = notificationPrefs.getBoolean(NOTIFICATION_SENT_KEY, false)

        return lastNotificationMonth == currentMonthYear && notificationSent
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_add_expense, null)

        dialogView.findViewById<TextView>(R.id.addExpenseTitle).visibility = View.GONE
        dialogView.findViewById<LinearLayout>(R.id.addExpenseButtonGroup).visibility = View.GONE
        val nameInput = dialogView.findViewById<EditText>(R.id.expenseNameInput)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.categorySpinner)
        val dateInput = dialogView.findViewById<TextView>(R.id.dateInput)
        val amountInput = dialogView.findViewById<EditText>(R.id.amountInput)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = adapter

        nameInput.setText(expense.name)
        val categoryIndex = categories.indexOf(expense.category)
        if (categoryIndex >= 0) categorySpinner.setSelection(categoryIndex)
        dateInput.text = expense.date
        amountInput.setText(expense.amount)

        dateInput.setTextColor(resources.getColor(R.color.black, null))
        amountInput.setTextColor(resources.getColor(R.color.black, null))

        val calendar = Calendar.getInstance()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            val date = dateFormatter.parse(expense.date)
            if (date != null) calendar.time = date
        } catch (e: Exception) {}

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

                    val updatedExpense = Expense(iconResId, name, category, date, expense.time, amount, categoryId, expense.id)

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val url = URL("http://192.168.1.100:8082/api/expenses/update/${expense.id}")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "PUT"
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.doOutput = true

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
                                    SnackbarUtil.showSuccess(requireView(), "Expense updated successfully", 3000)
                                    loadExpenses()
                                    updateBudgetInfo()
                                    fetchAndDisplayTotalExpenses()
                                    // Use the NotificationHelper's method if it exists
                                    try {
                                        notificationHelper.checkAndShowBudgetAlertIfNeeded()
                                    } catch (e: Exception) {
                                        // If the method doesn't exist, handle gracefully
                                        e.printStackTrace()
                                    }
                                } else {
                                    Toast.makeText(requireContext(), "Failed to update expense", Toast.LENGTH_SHORT).show()
                                }
                            }

                            connection.disconnect()

                        } catch (e: Exception) {
                            e.printStackTrace()
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val url = URL("http://192.168.1.100:8082/api/expenses/${expense.id}")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "DELETE"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000

                        val responseCode = connection.responseCode

                        requireActivity().runOnUiThread {
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                SnackbarUtil.showWarning(requireView(), "Expense deleted", 2500)
                                loadExpenses()
                                updateBudgetInfo()
                                fetchAndDisplayTotalExpenses()
                                // Use the NotificationHelper's method if it exists
                                try {
                                    notificationHelper.checkAndShowBudgetAlertIfNeeded()
                                } catch (e: Exception) {
                                    // If the method doesn't exist, handle gracefully
                                    e.printStackTrace()
                                }
                            } else {
                                Toast.makeText(requireContext(), "Failed to delete expense", Toast.LENGTH_SHORT).show()
                            }
                        }

                        connection.disconnect()

                    } catch (e: Exception) {
                        e.printStackTrace()
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}