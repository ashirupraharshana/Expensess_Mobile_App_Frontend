package com.example.finbot.fragment

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.finbot.R
import com.example.finbot.adapter.EarningsAdapter
import com.example.finbot.model.Earning
import com.example.finbot.util.SharedPreferencesManager
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class earningFragment : Fragment() {

    private lateinit var earningsRecyclerView: RecyclerView
    private lateinit var totalEarningsText: TextView
    private lateinit var totalSavingsText: TextView
    private lateinit var noEarningsText: TextView
    private lateinit var sharedPrefsManager: SharedPreferencesManager

    private lateinit var earningsAdapter: EarningsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.earning, container, false)

        // Initialize SharedPreferencesManager
        sharedPrefsManager = SharedPreferencesManager.getInstance(requireContext())

        // Initialize views
        earningsRecyclerView = view.findViewById(R.id.earningsRecyclerView)
        totalEarningsText = view.findViewById(R.id.totalEarningsText)
        totalSavingsText = view.findViewById(R.id.totalSavingsText)
        noEarningsText = view.findViewById(R.id.noEarningsText)

        // Set up RecyclerView
        earningsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Add Earning Button
        view.findViewById<Button>(R.id.addEarningButton).setOnClickListener {
            showAddEarningDialog()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadEarnings()
        fetchAndDisplayTotalSavings()
    }

    private fun loadEarnings() {
        // Check if fragment is attached before starting async operation
        if (!isAdded || context == null) return

        val userId = getUserIdFromSession()
        val currency = sharedPrefsManager.getCurrency()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/earnings/user/$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }

                    // Parse JSON manually (or use a proper library like Moshi/Gson)
                    val jsonArray = org.json.JSONArray(response)
                    val earningsList = mutableListOf<Earning>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val earning = Earning(
                            obj.getString("id"),
                            obj.getString("category"),
                            obj.getDouble("amount"),
                            obj.getString("date"),
                            obj.getString("time"),
                            obj.getString("userId")
                        )
                        earningsList.add(earning)
                    }

                    // Check if fragment is still attached before updating UI
                    if (isAdded && activity != null && context != null) {
                        activity?.runOnUiThread {
                            // Double-check before accessing views
                            if (isAdded && context != null) {
                                if (earningsList.isEmpty()) {
                                    earningsRecyclerView.visibility = View.GONE
                                    noEarningsText.visibility = View.VISIBLE
                                } else {
                                    earningsRecyclerView.visibility = View.VISIBLE
                                    noEarningsText.visibility = View.GONE
                                    earningsAdapter = EarningsAdapter(
                                        requireContext(),
                                        earningsList,
                                        { earning -> showEditEarningDialog(earning) },
                                        { earning -> showDeleteEarningDialog(earning) }
                                    )
                                    earningsRecyclerView.adapter = earningsAdapter
                                }

                                // Calculate total
                                val totalEarnings = earningsList.sumOf { it.amount }
                                totalEarningsText.text = "$currency ${String.format("%.2f", totalEarnings)}"

                                val totalExpenses = sharedPrefsManager.getCurrentMonthExpenses().toDouble()
                                val totalSavings = totalEarnings - totalExpenses
                                totalSavingsText.text = "$currency ${String.format("%.2f", totalSavings)}"
                            }
                        }
                    }

                } else {
                    if (isAdded && activity != null && context != null) {
                        activity?.runOnUiThread {
                            if (isAdded && context != null) {
                                Toast.makeText(context, "Failed to load earnings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showAddEarningDialog() {
        if (!isAdded || context == null) return

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_earning, null)
        val categoryInput = dialogView.findViewById<EditText>(R.id.categoryInput)
        val amountInput = dialogView.findViewById<EditText>(R.id.amountInput)
        val dateInput = dialogView.findViewById<TextView>(R.id.dateInput)
        dateInput.setTextColor(resources.getColor(R.color.black, null))
        amountInput.setTextColor(resources.getColor(R.color.black, null))

        // Set up calendar for date selection
        val calendar = Calendar.getInstance()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Set default date
        dateInput.text = dateFormatter.format(calendar.time)

        // Setup date picker when clicking on the date field
        dateInput.setOnClickListener {
            if (!isAdded || context == null) return@setOnClickListener

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                dateInput.text = dateFormatter.format(calendar.time)
            }, year, month, day).show()
        }

        // Show dialog
        AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Add Earning")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val category = categoryInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull()
                val date = dateInput.text.toString()

                if (category.isNotEmpty() && amount != null) {
                    addEarning(category, amount, date)
                    if (isAdded && context != null) {
                        Toast.makeText(context, "Earning added successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (isAdded && context != null) {
                        Toast.makeText(context, "Please enter valid details.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditEarningDialog(earning: Earning) {
        if (!isAdded || context == null) return

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_earning, null)
        val categoryInput = dialogView.findViewById<EditText>(R.id.categoryInput)
        val amountInput = dialogView.findViewById<EditText>(R.id.amountInput)
        val dateInput = dialogView.findViewById<TextView>(R.id.dateInput)

        // Pre-fill with existing values
        categoryInput.setText(earning.category)
        amountInput.setText(earning.amount.toString())
        dateInput.text = earning.date

        dateInput.setTextColor(resources.getColor(R.color.black, null))
        amountInput.setTextColor(resources.getColor(R.color.black, null))

        // Set up calendar for date selection
        val calendar = Calendar.getInstance()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            // Parse existing date
            val date = dateFormatter.parse(earning.date)
            if (date != null) {
                calendar.time = date
            }
        } catch (e: Exception) {
            // Use current date if parsing fails
        }

        // Setup date picker when clicking on the date field
        dateInput.setOnClickListener {
            if (!isAdded || context == null) return@setOnClickListener

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                dateInput.text = dateFormatter.format(calendar.time)
            }, year, month, day).show()
        }

        // Show dialog
        AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Edit Earning")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val category = categoryInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull()
                val date = dateInput.text.toString()

                if (category.isNotEmpty() && amount != null) {
                    val updatedEarning = Earning(earning.id, category, amount, date, earning.time, earning.userId)
                    updateEarning(earning, updatedEarning)
                    if (isAdded && context != null) {
                        Toast.makeText(context, "Earning updated!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (isAdded && context != null) {
                        Toast.makeText(context, "Please enter valid details.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchAndDisplayTotalEarnings() {
        if (!isAdded || context == null) return

        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/earnings/total?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val totalEarnings = response.toDoubleOrNull() ?: 0.0

                    if (isAdded && activity != null && context != null) {
                        activity?.runOnUiThread {
                            if (isAdded && context != null) {
                                totalEarningsText.text = "$currency ${String.format("%.2f", totalEarnings)}"
                            }
                        }
                    }
                } else {
                    if (isAdded && activity != null && context != null) {
                        activity?.runOnUiThread {
                            if (isAdded && context != null) {
                                Toast.makeText(context, "Failed to fetch earnings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun fetchAndDisplayTotalSavings() {
        if (!isAdded || context == null) return

        val currency = sharedPrefsManager.getCurrency()
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch total earnings
                val earningsUrl = URL("http://192.168.1.100:8082/api/earnings/total?userId=$userId")
                val earningsConnection = earningsUrl.openConnection() as HttpURLConnection
                earningsConnection.requestMethod = "GET"

                val earningsResponseCode = earningsConnection.responseCode
                var totalEarnings = 0.0

                if (earningsResponseCode == HttpURLConnection.HTTP_OK) {
                    val earningsResponse = earningsConnection.inputStream.bufferedReader().use { it.readText() }
                    totalEarnings = earningsResponse.toDoubleOrNull() ?: 0.0
                }
                earningsConnection.disconnect()

                // Fetch total expenses
                val expenseUrl = URL("http://192.168.1.100:8082/api/expenses/total?userId=$userId")
                val expenseConnection = expenseUrl.openConnection() as HttpURLConnection
                expenseConnection.requestMethod = "GET"

                val expenseResponseCode = expenseConnection.responseCode
                var totalExpenses = 0.0

                if (expenseResponseCode == HttpURLConnection.HTTP_OK) {
                    val expenseResponse = expenseConnection.inputStream.bufferedReader().use { it.readText() }
                    totalExpenses = expenseResponse.toDoubleOrNull() ?: 0.0
                }
                expenseConnection.disconnect()

                // Calculate total savings (earnings - expenses)
                val totalSavings = totalEarnings - totalExpenses

                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            totalEarningsText.text = "$currency ${String.format("%.2f", totalEarnings)}"
                            totalSavingsText.text = "$currency ${String.format("%.2f", totalSavings)}"
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Error fetching savings: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteEarningDialog(earning: Earning) {
        if (!isAdded || context == null) return

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Earning")
            .setMessage("Are you sure you want to delete this earning?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEarning(earning)
                if (isAdded && context != null) {
                    Toast.makeText(context, "Earning deleted!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCurrentTime(): String {
        val formatter = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date())
    }

    private fun getUserIdFromSession(): String {
        return if (isAdded && context != null) {
            val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
            sharedPref.getString("user_id", "") ?: ""
        } else {
            ""
        }
    }
    private fun addEarning(category: String, amount: Double, date: String) {
        if (!isAdded || context == null) return

        val time = getCurrentTime() // Optional: get current time if needed
        val userId = getUserIdFromSession() // Retrieve from session if needed

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/earnings/add")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonObject = org.json.JSONObject().apply {
                    put("category", category)
                    put("amount", amount)
                    put("date", date)
                    put("time", time)
                    put("userId", userId) // Optional if required
                }

                val outputStream = connection.outputStream
                outputStream.write(jsonObject.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode

                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                                Toast.makeText(context, "Earning added successfully", Toast.LENGTH_SHORT).show()
                                loadEarnings() // reload data from backend
                                fetchAndDisplayTotalSavings() // Update total savings immediately
                            } else {
                                Toast.makeText(context, "Failed to add earning", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                connection.disconnect()

            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }


    private fun updateEarning(oldEarning: Earning, newEarning: Earning) {
        if (!isAdded || context == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/earnings/update/${oldEarning.id}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonObject = org.json.JSONObject().apply {
                    put("category", newEarning.category)
                    put("amount", newEarning.amount)
                    put("date", newEarning.date)
                    put("time", newEarning.time)
                    put("userId", newEarning.userId)
                }

                val outputStream = connection.outputStream
                outputStream.write(jsonObject.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode

                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                Toast.makeText(context, "Earning updated successfully", Toast.LENGTH_SHORT).show()
                                loadEarnings()
                                fetchAndDisplayTotalEarnings()
                            } else {
                                Toast.makeText(context, "Failed to update earning", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                connection.disconnect()

            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun deleteEarning(earning: Earning) {
        if (!isAdded || context == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.1.100:8082/api/earnings/delete/${earning.id}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "DELETE"

                val responseCode = connection.responseCode

                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                Toast.makeText(context, "Earning deleted successfully", Toast.LENGTH_SHORT).show()
                                loadEarnings()
                                fetchAndDisplayTotalEarnings()
                            } else {
                                Toast.makeText(context, "Failed to delete earning", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                connection.disconnect()

            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded && activity != null && context != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateTotals() {
        if (!isAdded || context == null) return

        val earnings = sharedPrefsManager.getEarnings()
        val totalEarnings = earnings.sumOf { it.amount }
        val currency = sharedPrefsManager.getCurrency()

        totalEarningsText.text = "$currency ${String.format("%.2f", totalEarnings)}"

        // Calculate real savings: total earnings - total expenses
        val totalExpenses = sharedPrefsManager.getCurrentMonthExpenses().toDouble()
        val totalSavings = totalEarnings - totalExpenses
        totalSavingsText.text = "$currency ${String.format("%.2f", totalSavings)}"
    }
}