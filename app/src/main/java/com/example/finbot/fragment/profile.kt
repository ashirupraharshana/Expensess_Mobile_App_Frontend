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
import androidx.lifecycle.lifecycleScope
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
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.finbot.util.ThemeManager

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
    private lateinit var darkModeButton: LinearLayout
    private lateinit var darkModeIcon: ImageView
    private lateinit var darkModeText: TextView

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

        // Setup dark mode toggle
        setupDarkModeToggle(view)

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
    private fun setupDarkModeToggle(view: View) {
        darkModeButton = view.findViewById(R.id.darkModeButton)
        darkModeIcon = view.findViewById(R.id.darkModeIcon)
        darkModeText = view.findViewById(R.id.darkModeText)

        // Load and apply current theme state
        updateDarkModeUI()
        darkModeButton.visibility = View.GONE
        // Set click listener for dark mode toggle
        darkModeButton.setOnClickListener {
            toggleDarkMode()
        }
    }

    private fun getCurrencySymbol(currencyCode: Int): String {
        return when (currencyCode) {
            0 -> "LKR"  // Sri Lankan Rupee
            1 -> "$"    // US Dollar
            2 -> "€"    // Euro
            3 -> "£"    // British Pound
            4 -> "₹"    // Indian Rupee
            5 -> "A$"   // Australian Dollar
            else -> "LKR" // Default
        }
    }

    private fun toggleDarkMode() {
        val currentTheme = ThemeManager.getSelectedTheme(requireContext())
        val newTheme = when (currentTheme) {
            ThemeManager.THEME_DARK -> ThemeManager.THEME_LIGHT
            ThemeManager.THEME_LIGHT -> ThemeManager.THEME_DARK
            ThemeManager.THEME_SYSTEM -> {
                // If system theme, toggle to opposite of current appearance
                if (ThemeManager.isDarkMode(requireContext())) {
                    ThemeManager.THEME_LIGHT
                } else {
                    ThemeManager.THEME_DARK
                }
            }
            else -> ThemeManager.THEME_LIGHT
        }

        // Set the new theme
        ThemeManager.setTheme(requireContext(), newTheme)

        // Update UI text immediately
        updateDarkModeUI()

        // Show feedback to user
        val themeName = when (newTheme) {
            ThemeManager.THEME_DARK -> "Dark"
            ThemeManager.THEME_LIGHT -> "Light"
            else -> "System"
        }
        showSnackbar("Theme changed to $themeName mode")

        // Recreate activity to fully apply theme changes
        requireActivity().recreate()
    }

    private fun updateDarkModeUI() {
        if (!isAdded || !::darkModeText.isInitialized) return

        val currentTheme = ThemeManager.getSelectedTheme(requireContext())
        val isDarkMode = when (currentTheme) {
            ThemeManager.THEME_DARK -> true
            ThemeManager.THEME_LIGHT -> false
            ThemeManager.THEME_SYSTEM -> ThemeManager.isDarkMode(requireContext())
            else -> false
        }

        darkModeText.text = if (isDarkMode) {
            "Switch to Light Mode"
        } else {
            "Switch to Dark Mode"
        }

        // Update icon if you have different icons for light/dark
        // darkModeIcon.setImageResource(if (isDarkMode) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
    }
    override fun onResume() {
        super.onResume()
        // Update dark mode UI in case theme was changed elsewhere
        updateDarkModeUI()
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

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.22.87:8082/api/users/username/$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    val username = response.trim()

                    // Check if fragment is still attached before UI operations
                    if (isAdded && context != null) {
                        requireActivity().runOnUiThread {
                            if (isAdded && ::userNameInput.isInitialized) {
                                userNameInput.setText(username)
                            }
                        }
                    }
                } else {
                    if (isAdded && context != null) {
                        requireActivity().runOnUiThread {
                            if (isAdded) {
                                showSnackbar("Failed to load user profile")
                            }
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && context != null) {
                    requireActivity().runOnUiThread {
                        if (isAdded) {
                            showSnackbar("Error loading profile: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun createDefaultBudgetForUser() {
        if (!isAdded || context == null) return

        val userId = getUserIdFromSession()
        if (userId.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.22.87:8082/api/budget/save")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("userId", userId)
                    put("budget", 0.0)
                    put("currency", 0)
                    put("notificationsEnabled", true)
                    put("reminderEnabled", false)
                    put("alertPercent", 80)
                }

                val outputStream = connection.outputStream
                outputStream.write(jsonBody.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK && isAdded && context != null) {
                    requireActivity().runOnUiThread {
                        if (isAdded) {
                            loadBudgetSettingsFromBackend()
                            loadNotificationSettingsFromBackend()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error silently since this is a background operation
            }
        }
    }

    private fun loadBudgetSettingsFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) return

        // Check if fragment is still attached before starting coroutine
        if (!isAdded || context == null) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.22.87:8082/api/budget/get?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    if (response.isNullOrEmpty() || response.trim().isEmpty()) {
                        if (isAdded && context != null) {
                            createDefaultBudgetForUser()
                        }
                        connection.disconnect()
                        return@launch
                    }

                    try {
                        val jsonObject = JSONObject(response)
                        val budget = jsonObject.optDouble("budget", 0.0)
                        val currencyIndex = jsonObject.optInt("currency", 0)

                        if (isAdded && context != null) {
                            requireActivity().runOnUiThread {
                                if (isAdded && ::monthlyBudgetInput.isInitialized && ::currencySpinner.isInitialized) {
                                    monthlyBudgetInput.setText(budget.toString())
                                    if (currencyIndex >= 0 && currencyIndex < currencies.size) {
                                        currencySpinner.setSelection(currencyIndex)
                                    } else {
                                        currencySpinner.setSelection(0)
                                    }
                                }
                            }
                        }
                    } catch (jsonException: Exception) {
                        if (isAdded && context != null) {
                            createDefaultBudgetForUser()
                        }
                    }
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    if (isAdded && context != null) {
                        createDefaultBudgetForUser()
                    }
                } else {
                    if (isAdded && context != null) {
                        createDefaultBudgetForUser()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && context != null) {
                    createDefaultBudgetForUser()
                }
            }
        }
    }

    private fun loadNotificationSettingsFromBackend() {
        val userId = getUserIdFromSession()
        if (userId.isEmpty()) return

        if (!isAdded || context == null) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.22.87:8082/api/budget/get?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    if (response.isNullOrEmpty() || response.trim().isEmpty()) {
                        setDefaultNotificationSettings()
                        connection.disconnect()
                        return@launch
                    }

                    try {
                        val jsonObject = JSONObject(response)
                        val notificationsEnabled = jsonObject.optBoolean("notificationsEnabled", true)
                        val reminderEnabled = jsonObject.optBoolean("reminderEnabled", false)
                        val alertPercent = jsonObject.optInt("alertPercent", 80)

                        if (isAdded && context != null) {
                            requireActivity().runOnUiThread {
                                if (isAdded && ::budgetAlertsSwitch.isInitialized) {
                                    budgetAlertsSwitch.isChecked = notificationsEnabled
                                    dailyReminderSwitch.isChecked = reminderEnabled
                                    alertThresholdSeekBar.progress = alertPercent
                                    updateThresholdText(alertPercent)
                                    alertThresholdLayout.visibility = if (notificationsEnabled) View.VISIBLE else View.GONE
                                }
                            }
                        }
                    } catch (jsonException: Exception) {
                        setDefaultNotificationSettings()
                    }
                } else {
                    setDefaultNotificationSettings()
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                setDefaultNotificationSettings()
            }
        }
    }

    private fun setDefaultNotificationSettings() {
        if (isAdded && context != null) {
            requireActivity().runOnUiThread {
                if (isAdded && ::budgetAlertsSwitch.isInitialized) {
                    budgetAlertsSwitch.isChecked = true
                    dailyReminderSwitch.isChecked = false
                    alertThresholdSeekBar.progress = 80
                    updateThresholdText(80)
                    alertThresholdLayout.visibility = View.VISIBLE
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
        if (isAdded && ::thresholdTextView.isInitialized) {
            thresholdTextView.text = "$progress% of monthly budget"
        }
    }

    private fun saveUserProfile() {
        if (!isAdded || context == null) return

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

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.22.87:8082/api/users/username/$userId")
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

                if (isAdded && context != null) {
                    requireActivity().runOnUiThread {
                        if (isAdded && context != null) {
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                val snackbar = Snackbar.make(requireView(), "Username updated successfully!", Snackbar.LENGTH_SHORT)
                                snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                snackbar.show()
                            } else {
                                val snackbar = Snackbar.make(requireView(), "Failed to update username", Snackbar.LENGTH_SHORT)
                                snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                snackbar.show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && context != null) {
                    requireActivity().runOnUiThread {
                        if (isAdded && context != null) {
                            val snackbar = Snackbar.make(requireView(), "Error: ${e.message}", Snackbar.LENGTH_SHORT)
                            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                            snackbar.show()
                        }
                    }
                }
            }
        }
    }

    private fun getUserIdFromSession(): String {
        return if (isAdded && context != null) {
            try {
                val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
                sharedPref.getString("user_id", "") ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        } else {
            ""
        }
    }

    private fun saveBudgetSettings() {
        if (!isAdded || context == null) return

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

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val budgetUrl = URL("http://192.168.22.87:8082/api/budget/update/budget?userId=$userId&budget=$budget")
                    val budgetConnection = budgetUrl.openConnection() as HttpURLConnection
                    budgetConnection.requestMethod = "PUT"
                    budgetConnection.setRequestProperty("Content-Type", "application/json")

                    val budgetResponseCode = budgetConnection.responseCode
                    budgetConnection.disconnect()

                    if (budgetResponseCode == HttpURLConnection.HTTP_OK) {
                        val currencyUrl = URL("http://192.168.22.87:8082/api/budget/update/currency?userId=$userId&currency=$currencyIndex")
                        val currencyConnection = currencyUrl.openConnection() as HttpURLConnection
                        currencyConnection.requestMethod = "PUT"
                        currencyConnection.setRequestProperty("Content-Type", "application/json")

                        val currencyResponseCode = currencyConnection.responseCode
                        currencyConnection.disconnect()

                        if (isAdded && context != null) {
                            requireActivity().runOnUiThread {
                                if (isAdded) {
                                    if (currencyResponseCode == HttpURLConnection.HTTP_OK) {
                                        val snackbar = Snackbar.make(requireView(), "Budget settings saved successfully", Snackbar.LENGTH_SHORT)
                                        snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                        snackbar.show()
                                    } else {
                                        val snackbar = Snackbar.make(requireView(), "Failed to save currency setting", Snackbar.LENGTH_SHORT)
                                        snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                        snackbar.show()
                                    }
                                }
                            }
                        }
                    } else {
                        if (isAdded && context != null) {
                            requireActivity().runOnUiThread {
                                if (isAdded) {
                                    val snackbar = Snackbar.make(requireView(), "Failed to save budget setting", Snackbar.LENGTH_SHORT)
                                    snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                    snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                    snackbar.show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (isAdded && context != null) {
                        requireActivity().runOnUiThread {
                            if (isAdded) {
                                val snackbar = Snackbar.make(requireView(), "Error: ${e.message}", Snackbar.LENGTH_SHORT)
                                snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                snackbar.show()
                            }
                        }
                    }
                }
            }
        } catch (e: NumberFormatException) {
            val snackbar = Snackbar.make(requireView(), "Please enter a valid number for budget", Snackbar.LENGTH_SHORT)
            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
            snackbar.show()
        }
    }

    private fun saveNotificationSettings() {
        if (!isAdded || context == null) return

        val userId = getUserIdFromSession()
        if (userId.isEmpty()) {
            showSnackbar("User ID not found")
            return
        }

        val notificationsEnabled = budgetAlertsSwitch.isChecked
        val reminderEnabled = dailyReminderSwitch.isChecked
        val alertPercent = alertThresholdSeekBar.progress

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.22.87:8082/api/budget/update/notifications?userId=$userId&notificationsEnabled=$notificationsEnabled&reminderEnabled=$reminderEnabled&alertPercent=$alertPercent")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")

                val responseCode = connection.responseCode
                connection.disconnect()

                if (isAdded && context != null) {
                    requireActivity().runOnUiThread {
                        if (isAdded && ::notificationHelper.isInitialized) {
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                val snackbar = Snackbar.make(requireView(), "Notification settings saved", Snackbar.LENGTH_SHORT)
                                snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                snackbar.show()

                                if (dailyReminderSwitch.isChecked) {
                                    notificationHelper.scheduleDailyReminder()
                                } else {
                                    notificationHelper.cancelDailyReminder()
                                }
                            } else {
                                val snackbar = Snackbar.make(requireView(), "Failed to save notification settings", Snackbar.LENGTH_SHORT)
                                snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                                snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                                snackbar.show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && context != null) {
                    requireActivity().runOnUiThread {
                        if (isAdded) {
                            val snackbar = Snackbar.make(requireView(), "Error: ${e.message}", Snackbar.LENGTH_SHORT)
                            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                            snackbar.show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // viewLifecycleOwner.lifecycleScope automatically cancels all coroutines
    }
    private fun showSnackbar(message: String) {
        val snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT)
        snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
        snackbar.show()
    }
}