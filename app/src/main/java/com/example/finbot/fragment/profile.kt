package com.example.finbot.fragment

import android.app.Activity.RESULT_OK
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.finbot.R
import com.example.finbot.util.NotificationHelper
import com.example.finbot.util.SnackbarUtil
import com.google.android.material.snackbar.Snackbar
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class profileFragment : Fragment() {

    private lateinit var notificationHelper: NotificationHelper

    // UI components
    private lateinit var userNameInput: EditText
    private lateinit var monthlyBudgetInput: EditText
    private lateinit var currencySpinner: Spinner
    private lateinit var budgetAlertsSwitch: SwitchCompat
    private lateinit var dailyReminderSwitch: SwitchCompat
    private lateinit var alertThresholdSeekBar: SeekBar
    private lateinit var thresholdTextView: TextView
    private lateinit var alertThresholdLayout: LinearLayout

    private val currencies = arrayOf("LKR", "USD", "EUR", "GBP", "INR", "AUD")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.profile, container, false)

        // Initialize managers
        notificationHelper = NotificationHelper.getInstance(requireContext())

        // Initialize views
        initializeViews(view)
        loadSettings()
        setupListeners()

        return view
    }

    private fun initializeViews(view: View) {
        // User profile
        userNameInput = view.findViewById(R.id.userNameInput)
        view.findViewById<Button>(R.id.saveProfileButton).setOnClickListener { saveUserProfile() }

        // Budget settings
        monthlyBudgetInput = view.findViewById(R.id.monthlyBudgetInput)
        currencySpinner = view.findViewById(R.id.currencySpinner)
        val currencyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, currencies)
        currencySpinner.adapter = currencyAdapter
        view.findViewById<Button>(R.id.saveBudgetButton).setOnClickListener { saveBudgetSettings() }

        // Notification settings
        budgetAlertsSwitch = view.findViewById(R.id.budgetAlertsSwitch)
        dailyReminderSwitch = view.findViewById(R.id.dailyReminderSwitch)
        alertThresholdSeekBar = view.findViewById(R.id.alertThresholdSeekBar)
        thresholdTextView = view.findViewById(R.id.thresholdTextView)
        alertThresholdLayout = view.findViewById(R.id.alertThresholdLayout)
        view.findViewById<Button>(R.id.saveNotificationButton).setOnClickListener { saveNotificationSettings() }

        // Remove backup/restore buttons since we're using backend only
        view.findViewById<Button>(R.id.exportTextButton)?.visibility = View.GONE
        view.findViewById<Button>(R.id.restoreDataButton)?.visibility = View.GONE

        // Logout button
        val logoutButton = view.findViewById<Button>(R.id.logout)
        logoutButton.setOnClickListener {
            // Clear login state
            val sharedPref = requireActivity().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
            sharedPref.edit().clear().apply()

            // Navigate to Login activity
            val intent = Intent(requireContext(), com.example.finbot.Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            // Finish current activity
            requireActivity().finish()
        }
    }

    private fun loadSettings() {
        // Load all settings from backend
        loadUserProfileFromBackend()
        loadBudgetSettingsFromBackend()
        loadNotificationSettingsFromBackend()
    }
    private fun loadUserProfileFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Updated URL to match your backend endpoint
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
                        userNameInput.setText(username)
                    }
                } else {
                    requireActivity().runOnUiThread {
                        showSnackbar("Failed to load user profile")
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    showSnackbar("Error loading profile: ${e.message}")
                }
            }
        }
    }

    private fun loadBudgetSettingsFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/budget/get?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    // Check if response is empty or null
                    if (response.isNullOrEmpty() || response.trim().isEmpty()) {
                        requireActivity().runOnUiThread {
                            // Set default values if no budget found
                            monthlyBudgetInput.setText("0.0")
                            currencySpinner.setSelection(0) // Default to first currency (LKR)
                            showSnackbar("No budget settings found. Please set your budget.")
                        }
                        connection.disconnect()
                        return@launch
                    }

                    // Try to parse as JSON
                    try {
                        val jsonObject = JSONObject(response)
                        val budget = jsonObject.optDouble("budget", 0.0)
                        val currencyIndex = jsonObject.optInt("currency", 0)

                        requireActivity().runOnUiThread {
                            monthlyBudgetInput.setText(budget.toString())
                            if (currencyIndex >= 0 && currencyIndex < currencies.size) {
                                currencySpinner.setSelection(currencyIndex)
                            } else {
                                currencySpinner.setSelection(0) // Default to first currency
                            }
                        }
                    } catch (jsonException: Exception) {
                        // If JSON parsing fails, try to handle as plain text or error message
                        requireActivity().runOnUiThread {
                            if (response.contains("error", ignoreCase = true) ||
                                response.contains("not found", ignoreCase = true)) {
                                // Budget not found, set defaults
                                monthlyBudgetInput.setText("0.0")
                                currencySpinner.setSelection(0)
                                showSnackbar("No budget settings found. Please set your budget.")
                            } else {
                                // Try to parse as plain number (if backend returns just the budget value)
                                try {
                                    val budget = response.trim().toDouble()
                                    monthlyBudgetInput.setText(budget.toString())
                                    currencySpinner.setSelection(0) // Default currency
                                } catch (numberException: Exception) {
                                    // If all parsing fails, set defaults
                                    monthlyBudgetInput.setText("0.0")
                                    currencySpinner.setSelection(0)
                                    showSnackbar("Error parsing budget data. Please set your budget again.")
                                }
                            }
                        }
                    }
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    // Budget not found for user
                    requireActivity().runOnUiThread {
                        monthlyBudgetInput.setText("0.0")
                        currencySpinner.setSelection(0)
                        showSnackbar("No budget found. Please set your monthly budget.")
                    }
                } else {
                    // Other HTTP errors
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"

                    requireActivity().runOnUiThread {
                        monthlyBudgetInput.setText("0.0")
                        currencySpinner.setSelection(0)
                        showSnackbar("Failed to load budget settings: $errorResponse")
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    // Set default values on error
                    monthlyBudgetInput.setText("0.0")
                    currencySpinner.setSelection(0)
                    showSnackbar("Error loading budget settings: ${e.message}")
                }
            }
        }
    }
    private fun loadNotificationSettingsFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/notifications/get?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    // Check if response is empty or null
                    if (response.isNullOrEmpty() || response.trim().isEmpty()) {
                        requireActivity().runOnUiThread {
                            // Set default notification settings
                            budgetAlertsSwitch.isChecked = true // Default to enabled
                            dailyReminderSwitch.isChecked = false // Default to disabled
                            alertThresholdSeekBar.progress = 80 // Default to 80%
                            updateThresholdText(80)
                            alertThresholdLayout.visibility = View.VISIBLE // Show since alerts are enabled by default
                            showSnackbar("No notification settings found. Using defaults.")
                        }
                        connection.disconnect()
                        return@launch
                    }

                    // Try to parse as JSON
                    try {
                        val jsonObject = JSONObject(response)
                        val notificationsEnabled = jsonObject.optBoolean("notificationsEnabled", true)
                        val reminderEnabled = jsonObject.optBoolean("reminderEnabled", false)
                        val alertPercent = jsonObject.optInt("alertPercent", 80)

                        requireActivity().runOnUiThread {
                            budgetAlertsSwitch.isChecked = notificationsEnabled
                            dailyReminderSwitch.isChecked = reminderEnabled
                            alertThresholdSeekBar.progress = alertPercent
                            updateThresholdText(alertPercent)

                            // Update UI state based on settings
                            alertThresholdLayout.visibility = if (notificationsEnabled) View.VISIBLE else View.GONE
                        }
                    } catch (jsonException: Exception) {
                        // If JSON parsing fails, handle as error message or set defaults
                        requireActivity().runOnUiThread {
                            if (response.contains("error", ignoreCase = true) ||
                                response.contains("not found", ignoreCase = true)) {
                                // Notification settings not found, set defaults
                                budgetAlertsSwitch.isChecked = true
                                dailyReminderSwitch.isChecked = false
                                alertThresholdSeekBar.progress = 80
                                updateThresholdText(80)
                                alertThresholdLayout.visibility = View.VISIBLE
                                showSnackbar("No notification settings found. Using defaults.")
                            } else {
                                // Unexpected response format, set defaults
                                budgetAlertsSwitch.isChecked = true
                                dailyReminderSwitch.isChecked = false
                                alertThresholdSeekBar.progress = 80
                                updateThresholdText(80)
                                alertThresholdLayout.visibility = View.VISIBLE
                                showSnackbar("Error parsing notification settings. Using defaults.")
                            }
                        }
                    }
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    // Notification settings not found for user
                    requireActivity().runOnUiThread {
                        budgetAlertsSwitch.isChecked = true
                        dailyReminderSwitch.isChecked = false
                        alertThresholdSeekBar.progress = 80
                        updateThresholdText(80)
                        alertThresholdLayout.visibility = View.VISIBLE
                        showSnackbar("No notification settings found. Using defaults.")
                    }
                } else {
                    // Other HTTP errors
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"

                    requireActivity().runOnUiThread {
                        // Set default values on error
                        budgetAlertsSwitch.isChecked = true
                        dailyReminderSwitch.isChecked = false
                        alertThresholdSeekBar.progress = 80
                        updateThresholdText(80)
                        alertThresholdLayout.visibility = View.VISIBLE
                        showSnackbar("Failed to load notification settings: $errorResponse")
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    // Set default values on error
                    budgetAlertsSwitch.isChecked = true
                    dailyReminderSwitch.isChecked = false
                    alertThresholdSeekBar.progress = 80
                    updateThresholdText(80)
                    alertThresholdLayout.visibility = View.VISIBLE
                    showSnackbar("Error loading notification settings: ${e.message}")
                }
            }
        }
    }


    private fun setupListeners() {
        // Notification switches
        budgetAlertsSwitch.setOnCheckedChangeListener { _, isChecked ->
            alertThresholdLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Threshold seekbar
        alertThresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateThresholdText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateThresholdText(progress: Int) {
        thresholdTextView.text = "$progress% of monthly budget"
    }

    private fun saveUserProfile() {
        val newUsername = userNameInput.text.toString().trim()
        val userId = getUserIdFromSession()

        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "User ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        if (newUsername.isEmpty()) {
            Toast.makeText(requireContext(), "Username cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Updating username...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Updated URL to match your preferred endpoint
                val url = URL("http://192.168.1.100:8082/api/users/username/$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("username", newUsername)
                }

                val outputStream = connection.outputStream
                outputStream.write(jsonBody.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode

                requireActivity().runOnUiThread {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(requireContext(), "Username updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update username", Toast.LENGTH_SHORT).show()
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

    private fun getUserIdFromSession(): String {
        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        return sharedPref.getString("user_id", "") ?: ""
    }

    private fun saveBudgetSettings() {
        val budgetStr = monthlyBudgetInput.text.toString().trim()
        if (budgetStr.isEmpty()) {
            showSnackbar("Please enter monthly budget")
            return
        }

        try {
            val budget = budgetStr.toDouble()
            val currencyIndex = currencySpinner.selectedItemPosition
            val userId = getUserIdFromSession()

            if (userId.isEmpty()) {
                showSnackbar("User ID not found")
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL("http://192.168.1.100:8082/api/budget/save")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true

                    val jsonObject = JSONObject().apply {
                        put("userId", userId)
                        put("budget", budget)
                        put("currency", currencyIndex)
                    }

                    connection.outputStream.use { output ->
                        output.write(jsonObject.toString().toByteArray())
                        output.flush()
                    }

                    val responseCode = connection.responseCode
                    requireActivity().runOnUiThread {
                        if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                            showSnackbar("Budget settings saved successfully")
                        } else {
                            showSnackbar("Failed to save budget settings")
                        }
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                    requireActivity().runOnUiThread {
                        showSnackbar("Error: ${e.message}")
                    }
                }
            }
        } catch (e: NumberFormatException) {
            showSnackbar("Please enter a valid number for budget")
        }
    }

    private fun saveNotificationSettings() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) {
            showSnackbar("User ID not found")
            return
        }

        val jsonObject = JSONObject().apply {
            put("userId", userId)
            put("notificationsEnabled", budgetAlertsSwitch.isChecked)
            put("reminderEnabled", dailyReminderSwitch.isChecked)
            put("alertPercent", alertThresholdSeekBar.progress)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/notifications/save")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                connection.outputStream.use {
                    it.write(jsonObject.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                requireActivity().runOnUiThread {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        showSnackbar("Notification settings saved")

                        // Handle local notification scheduling
                        if (dailyReminderSwitch.isChecked) {
                            notificationHelper.scheduleDailyReminder()
                        } else {
                            notificationHelper.cancelDailyReminder()
                        }
                    } else {
                        showSnackbar("Failed to save notification settings")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    showSnackbar("Error: ${e.message}")
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        SnackbarUtil.showSuccess(requireView(), message, 3000)
    }
}