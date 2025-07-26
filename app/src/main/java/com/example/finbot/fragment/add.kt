package com.example.finbot.fragment

import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.finbot.MainActivity
import com.example.finbot.R
import com.example.finbot.util.NotificationHelper
import com.example.finbot.util.SharedPreferencesManager
import com.example.finbot.util.SnackbarUtil
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.net.URL
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import java.util.*
import android.graphics.Color
import androidx.core.content.ContextCompat

class AddExpenseFragment : Fragment() {

    private lateinit var expenseNameInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var dateInput: TextView
    private lateinit var amountInput: EditText
    private lateinit var submitButton: Button
    private lateinit var cancelButton: Button
    private val calendar = Calendar.getInstance()
    
    private lateinit var sharedPrefsManager: SharedPreferencesManager
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_expense, container, false)

        // Initialize managers
        sharedPrefsManager = SharedPreferencesManager.getInstance(requireContext())
        notificationHelper = NotificationHelper.getInstance(requireContext())

        // Initialize views
        expenseNameInput = view.findViewById(R.id.expenseNameInput)
        categorySpinner = view.findViewById(R.id.categorySpinner)
        dateInput = view.findViewById(R.id.dateInput)
        amountInput = view.findViewById(R.id.amountInput)
        submitButton = view.findViewById(R.id.submitButton)
        cancelButton = view.findViewById(R.id.cancelButton)

        setupViews()
        return view
    }

    private fun setupViews() {
        // Setup Category spinner
        val categories = arrayOf("Food", "Shopping", "Transport", "Health", "Utility", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = adapter

        // Setup date picker
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateInput.text = dateFormatter.format(calendar.time)

        dateInput.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                dateInput.text = dateFormatter.format(calendar.time)
            }, year, month, day).show()
        }

        // Setup buttons
        submitButton.setOnClickListener {
            saveExpense()
        }

        cancelButton.setOnClickListener {
            // Navigate back to previous fragment
            requireActivity().supportFragmentManager.popBackStack()
        }
    }
    private fun getUserIdFromSession(): String {
        val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
        return sharedPref.getString("user_id", "") ?: ""
    }

    private fun saveExpense() {
        val name = expenseNameInput.text.toString().trim()
        val category = categorySpinner.selectedItem.toString()
        val date = dateInput.text.toString()
        val amount = amountInput.text.toString().trim()
        val userId = getUserIdFromSession()

        if (name.isNotEmpty() && amount.isNotEmpty()) {
            val categoryId = getCategoryId(category)

            //  Get current time in "HH:mm" format
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            // ✅ Create JSON object with time
            val expenseJson = JSONObject().apply {
                put("name", name)
                put("categoryId", categoryId)
                put("date", date)
                put("time", time)
                put("amount", amount)
                put("userId", userId)
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL("http://192.168.1.100:8082/api/expenses/add")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    val outputStream = DataOutputStream(conn.outputStream)
                    outputStream.writeBytes(expenseJson.toString())
                    outputStream.flush()
                    outputStream.close()

                    val responseCode = conn.responseCode
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    requireActivity().runOnUiThread {
                        if (responseCode == 200) {
                            val snackbar = Snackbar.make(requireView(), "Expense added successfully", 3000)
                            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                            snackbar.show()

                            notificationHelper.checkAndShowBudgetAlertIfNeeded()
                            (requireActivity() as MainActivity).loadFragment(
                                (requireActivity() as MainActivity).supportFragmentManager.findFragmentByTag("homeFragment")
                                    ?: com.example.finbot.fragments.homeFragment()
                            )
                        } else {
                            val snackbar = Snackbar.make(requireView(), "Failed to add expense: $responseText", 3000)
                            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_background_light))
                            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.snackbar_text_light))
                            snackbar.show()
                        }
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        val snackbar = Snackbar.make(requireView(), "Error: ${e.message}", 3000)
                        val snackbarView = snackbar.view
                        snackbarView.background = ContextCompat.getDrawable(requireContext(), R.drawable.snackbar_background)
                        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                        textView.setTextColor(Color.WHITE)
                        snackbar.show()
                    }
                }
            }

        } else {
            val toast = Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT)
            // If you want to style the Toast as well (optional)
            val toastView = toast.view
            toastView?.background = ContextCompat.getDrawable(requireContext(), R.drawable.snackbar_background)
            val toastText = toastView?.findViewById<TextView>(android.R.id.message)
            toastText?.setTextColor(Color.WHITE)
            toast.show()
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
}