package com.example.finbot.fragment

import android.content.Context.MODE_PRIVATE
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.Fragment
import com.example.finbot.R
import com.example.finbot.model.Earning
import com.example.finbot.model.Expense
import com.example.finbot.util.SharedPreferencesManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.chip.ChipGroup
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import org.json.JSONArray
import org.json.JSONObject

class statFragment : Fragment() {

    private lateinit var sharedPrefsManager: SharedPreferencesManager
    private lateinit var pieChart: PieChart
    private lateinit var lineChart: LineChart
    private lateinit var earningsLineChart: LineChart
    private lateinit var totalSpentText: TextView
    private lateinit var highestCategoryText: TextView
    private lateinit var periodChipGroup: ChipGroup
    private lateinit var legendContainer: FlexboxLayout

    private var currentPeriod = PERIOD_WEEK

    companion object {
        const val PERIOD_WEEK = 0
        const val PERIOD_MONTH = 1
        const val PERIOD_YEAR = 2
    }

    private fun getUserIdFromSession(): String {
        val sharedPref = requireContext().getSharedPreferences("user_session", MODE_PRIVATE)
        return sharedPref.getString("user_id", "") ?: ""
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

    private fun getDateRange(period: Int): Pair<Calendar, Calendar> {
        val endCalendar = Calendar.getInstance()
        val startCalendar = Calendar.getInstance()

        when (period) {
            PERIOD_WEEK -> {
                // Get current week (Monday to Sunday)
                val dayOfWeek = endCalendar.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

                startCalendar.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                startCalendar.set(Calendar.HOUR_OF_DAY, 0)
                startCalendar.set(Calendar.MINUTE, 0)
                startCalendar.set(Calendar.SECOND, 0)
                startCalendar.set(Calendar.MILLISECOND, 0)
            }
            PERIOD_MONTH -> {
                // Get current month
                startCalendar.set(Calendar.DAY_OF_MONTH, 1)
                startCalendar.set(Calendar.HOUR_OF_DAY, 0)
                startCalendar.set(Calendar.MINUTE, 0)
                startCalendar.set(Calendar.SECOND, 0)
                startCalendar.set(Calendar.MILLISECOND, 0)
            }
            PERIOD_YEAR -> {
                // Get current year
                startCalendar.set(Calendar.DAY_OF_YEAR, 1)
                startCalendar.set(Calendar.HOUR_OF_DAY, 0)
                startCalendar.set(Calendar.MINUTE, 0)
                startCalendar.set(Calendar.SECOND, 0)
                startCalendar.set(Calendar.MILLISECOND, 0)
            }
        }

        return Pair(startCalendar, endCalendar)
    }

    private fun fetchExpenses(period: Int) {
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.103.87:8082/api/expenses/user?userId=$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val expenses = mutableListOf<Expense>()
                    val jsonArray = JSONArray(response)

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)

                        val id = item.getString("id")
                        val name = item.getString("name")
                        val date = item.getString("date")
                        val time = if (item.has("time")) item.getString("time") else "00:00"
                        val amount = item.getString("amount")
                        val categoryId = item.getInt("categoryId")

                        val category = getCategoryFromId(categoryId)
                        val expense = Expense(0, name, category, date, time, amount, categoryId, id)
                        expenses.add(expense)
                    }

                    // Filter expenses by the selected period
                    val filtered = filterExpensesByPeriod(expenses, period)

                    withContext(Dispatchers.Main) {
                        updateSummaryCards(filtered)
                        setupPieChart(pieChart, filtered)
                        setupLineChart(lineChart, filtered, period)
                    }

                } else {
                    withContext(Dispatchers.Main) {
                        setupEmptyPieChart()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    setupEmptyPieChart()
                }
            }
        }
    }

    private fun fetchEarnings(period: Int) {
        val userId = getUserIdFromSession()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.103.87:8082/api/earnings/user/$userId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }

                    val earnings = mutableListOf<Earning>()
                    val jsonArray = JSONArray(response)

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
                        earnings.add(earning)
                    }

                    // Filter earnings by the selected period
                    val filtered = filterEarningsByPeriod(earnings, period)

                    withContext(Dispatchers.Main) {
                        setupEarningsLineChart(earningsLineChart, filtered, period)
                    }

                } else {
                    withContext(Dispatchers.Main) {
                        setupEmptyEarningsLineChart()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    setupEmptyEarningsLineChart()
                }
            }
        }
    }

    private fun filterExpensesByPeriod(expenses: List<Expense>, period: Int): List<Expense> {
        val (startCalendar, endCalendar) = getDateRange(period)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return expenses.filter {
            try {
                val expenseDate = dateFormat.parse(it.date)
                expenseDate != null &&
                        expenseDate.after(startCalendar.time) &&
                        expenseDate.before(endCalendar.time) ||
                        expenseDate == startCalendar.time ||
                        expenseDate == endCalendar.time
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun filterEarningsByPeriod(earnings: List<Earning>, period: Int): List<Earning> {
        val (startCalendar, endCalendar) = getDateRange(period)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return earnings.filter {
            try {
                val earningDate = dateFormat.parse(it.date)
                earningDate != null &&
                        earningDate.after(startCalendar.time) &&
                        earningDate.before(endCalendar.time) ||
                        earningDate == startCalendar.time ||
                        earningDate == endCalendar.time
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.stat, container, false)

        sharedPrefsManager = SharedPreferencesManager.getInstance(requireContext())

        pieChart = view.findViewById(R.id.pieChart)
        lineChart = view.findViewById(R.id.lineChart)
        earningsLineChart = view.findViewById(R.id.earningsLineChart)
        totalSpentText = view.findViewById(R.id.totalSpentText)
        highestCategoryText = view.findViewById(R.id.highestCategoryText)
        periodChipGroup = view.findViewById(R.id.periodChipGroup)
        legendContainer = view.findViewById(R.id.legendContainer)

        setupPeriodSelectionListeners()
        loadData(PERIOD_WEEK)

        return view
    }

    override fun onResume() {
        super.onResume()
        loadData(currentPeriod)
    }

    private fun setupPeriodSelectionListeners() {
        periodChipGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipWeek -> {
                    currentPeriod = PERIOD_WEEK
                    loadData(PERIOD_WEEK)
                }
                R.id.chipMonth -> {
                    currentPeriod = PERIOD_MONTH
                    loadData(PERIOD_MONTH)
                }
                R.id.chipYear -> {
                    currentPeriod = PERIOD_YEAR
                    loadData(PERIOD_YEAR)
                }
            }
        }
    }

    private fun loadData(period: Int) {
        try {
            fetchExpenses(period)
            fetchEarnings(period)
        } catch (e: Exception) {
            e.printStackTrace()
            totalSpentText.text = "${sharedPrefsManager.getCurrency()} 0.00"
            highestCategoryText.text = "None"
            setupEmptyPieChart()
            setupEmptyLineChart()
            setupEmptyEarningsLineChart()
        }
    }

    // Updated setupPieChart function
    private fun setupPieChart(pieChart: PieChart, expenses: List<Expense>) {
        try {
            val categoryMap = HashMap<String, Double>()
            expenses.forEach { expense ->
                val amount = expense.amount.toDoubleOrNull() ?: 0.0
                categoryMap[expense.category] = (categoryMap[expense.category] ?: 0.0) + amount
            }

            val entries = ArrayList<PieEntry>()
            val totalAmount = categoryMap.values.sum()

            if (totalAmount <= 0) {
                setupEmptyPieChart()
                return
            }

            val colorMap = mapOf(
                "Food" to getColor(requireContext(), R.color.food),
                "Shopping" to getColor(requireContext(), R.color.shopping),
                "Transport" to getColor(requireContext(), R.color.transport),
                "Health" to getColor(requireContext(), R.color.health),
                "Utility" to getColor(requireContext(), R.color.Blue),
                "Other" to getColor(requireContext(), R.color.others),)


            val colors = ArrayList<Int>()
            categoryMap.forEach { (category, amount) ->
                val percentage = (amount / totalAmount * 100).toFloat()
                entries.add(PieEntry(percentage, category))
                colors.add(colorMap[category] ?: Color.GREEN)
            }

            val dataSet = PieDataSet(entries, "Expense Distribution")
            dataSet.colors = colors
            dataSet.valueTextColor = getChartTextColor() // Theme-aware text color
            dataSet.valueTextSize = 12f

            val pieData = PieData(dataSet)

            pieChart.data = pieData
            pieChart.setUsePercentValues(true)
            pieChart.description.isEnabled = false
            pieChart.legend.isEnabled = false

            // Theme-aware styling for pie chart
            pieChart.setBackgroundColor(getChartBackgroundColor())
            pieChart.setHoleColor(getChartHoleColor())
            pieChart.setTransparentCircleColor(getChartHoleColor())
            pieChart.setDrawHoleEnabled(true)
            pieChart.holeRadius = 40f
            pieChart.transparentCircleRadius = 45f

            // Set center text color based on theme
            pieChart.centerText = "Expenses"
            pieChart.setCenterTextColor(getChartTextColor())
            pieChart.setCenterTextSize(16f)

            pieChart.animateY(1000)
            pieChart.invalidate()

            legendContainer.removeAllViews()

            entries.forEachIndexed { index, entry ->
                val legendItem = createLegendItem(colors[index], entry.label, "${entry.value.toInt()}%")
                val itemParams = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                legendItem.layoutParams = itemParams
                legendContainer.addView(legendItem)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            setupEmptyPieChart()
        }
    }

    // Updated setupEmptyPieChart function
    private fun setupEmptyPieChart() {
        try {
            val entries = ArrayList<PieEntry>()
            entries.add(PieEntry(100f, "No Data"))
            val dataSet = PieDataSet(entries, "Expense Distribution")
            dataSet.colors = listOf(Color.LTGRAY)
            dataSet.valueTextColor = getChartTextColor() // Theme-aware text color
            dataSet.valueTextSize = 12f

            val pieData = PieData(dataSet)
            pieChart.data = pieData
            pieChart.setUsePercentValues(true)
            pieChart.description.isEnabled = false
            pieChart.legend.isEnabled = false

            // Theme-aware styling for empty pie chart
            pieChart.setBackgroundColor(getChartBackgroundColor())
            pieChart.setHoleColor(getChartHoleColor())
            pieChart.setTransparentCircleColor(getChartHoleColor())
            pieChart.setDrawHoleEnabled(true)
            pieChart.holeRadius = 40f
            pieChart.transparentCircleRadius = 45f

            pieChart.centerText = "No Data"
            pieChart.setCenterTextColor(getChartTextColor()) // Theme-aware center text
            pieChart.setCenterTextSize(16f)

            pieChart.animateY(1000)
            pieChart.invalidate()

            legendContainer.removeAllViews()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun setupEmptyLineChart() {
        try {
            lineChart.setNoDataText("No expense data available")
            lineChart.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupEmptyEarningsLineChart() {
        try {
            earningsLineChart.setNoDataText("No earnings data available")
            earningsLineChart.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateSummaryCards(expenses: List<Expense>) {
        val totalSpent = expenses.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
        val currency = sharedPrefsManager.getCurrency()

        totalSpentText.text = "$currency ${String.format("%.2f", totalSpent)}"

        val categorySums = HashMap<String, Double>()
        expenses.forEach { expense ->
            val amount = expense.amount.toDoubleOrNull() ?: 0.0
            categorySums[expense.category] = (categorySums[expense.category] ?: 0.0) + amount
        }

        val highestCategory = categorySums.maxByOrNull { it.value }?.key ?: "None"
        highestCategoryText.text = highestCategory
    }



    private fun createLegendItem(color: Int, label: String, percentage: String): View {
        val inflater = LayoutInflater.from(requireContext())
        val legendItem = inflater.inflate(R.layout.legend_item, null) as LinearLayout

        val colorView = legendItem.findViewById<View>(R.id.legendColor)
        colorView.setBackgroundColor(color)

        val labelView = legendItem.findViewById<TextView>(R.id.legendLabel)
        labelView.text = label

        val percentageView = legendItem.findViewById<TextView>(R.id.legendPercentage)
        percentageView.text = percentage

        return legendItem
    }

    private fun isDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // Add this helper function to get theme-appropriate colors
    private fun getChartBackgroundColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#2C2C2C") // Dark background
        } else {
            Color.WHITE // Light background
        }
    }

    private fun getChartTextColor(): Int {
        return if (isDarkMode()) {
            Color.WHITE // White text for dark mode
        } else {
            Color.BLACK // Black text for light mode
        }
    }

    private fun getChartHoleColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#2C2C2C") // Dark hole color
        } else {
            Color.WHITE // Light hole color
        }
    }


    // Updated setupLineChart function
    private fun setupLineChart(lineChart: LineChart, expenses: List<Expense>, period: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val entries = ArrayList<Entry>()
        val (startCalendar, endCalendar) = getDateRange(period)

        when (period) {
            PERIOD_WEEK -> {
                // Group by days of the week
                val dailyExpenses = Array(7) { 0f }

                expenses.forEach { expense ->
                    try {
                        val expenseDate = dateFormat.parse(expense.date) ?: return@forEach
                        val expenseCalendar = Calendar.getInstance().apply { time = expenseDate }

                        val dayOfWeek = expenseCalendar.get(Calendar.DAY_OF_WEEK)
                        val index = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

                        val amount = expense.amount.toFloatOrNull() ?: 0f
                        dailyExpenses[index] += amount
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (i in dailyExpenses.indices) {
                    entries.add(Entry(i.toFloat(), dailyExpenses[i]))
                }
            }
            PERIOD_MONTH -> {
                // Group by weeks in the month
                val weeklyExpenses = Array(5) { 0f }

                expenses.forEach { expense ->
                    try {
                        val expenseDate = dateFormat.parse(expense.date) ?: return@forEach
                        val expenseCalendar = Calendar.getInstance().apply { time = expenseDate }

                        val weekOfMonth = expenseCalendar.get(Calendar.WEEK_OF_MONTH) - 1
                        val index = weekOfMonth.coerceIn(0, 4)

                        val amount = expense.amount.toFloatOrNull() ?: 0f
                        weeklyExpenses[index] += amount
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (i in weeklyExpenses.indices) {
                    entries.add(Entry(i.toFloat(), weeklyExpenses[i]))
                }
            }
            PERIOD_YEAR -> {
                // Group by months in the year
                val monthlyExpenses = Array(12) { 0f }

                expenses.forEach { expense ->
                    try {
                        val expenseDate = dateFormat.parse(expense.date) ?: return@forEach
                        val expenseCalendar = Calendar.getInstance().apply { time = expenseDate }

                        val month = expenseCalendar.get(Calendar.MONTH)
                        val amount = expense.amount.toFloatOrNull() ?: 0f
                        monthlyExpenses[month] += amount
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (i in monthlyExpenses.indices) {
                    entries.add(Entry(i.toFloat(), monthlyExpenses[i]))
                }
            }
        }

        if (entries.isEmpty() || entries.all { it.y <= 0f }) {
            lineChart.setNoDataText("No expense data available")
            lineChart.invalidate()
            return
        }

        val dataSet = LineDataSet(entries, "Spending Trends")
        dataSet.color = getColor(requireContext(), R.color.Blue)
        dataSet.valueTextColor = getChartTextColor() // Theme-aware value text color
        dataSet.valueTextSize = 10f
        dataSet.setCircleColor(getColor(requireContext(), R.color.Blue))
        dataSet.circleRadius = 4f

        val lineData = LineData(dataSet)

        lineChart.data = lineData
        lineChart.description.isEnabled = false

        // Configure Y-axis with theme-aware text
        lineChart.axisLeft.axisMinimum = 0f
        lineChart.axisLeft.textColor = getChartTextColor() // Theme-aware Y-axis labels
        lineChart.axisRight.isEnabled = false // Disable right axis

        // Configure X-axis with theme-aware text
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.granularity = 1f
        lineChart.xAxis.textColor = getChartTextColor() // Theme-aware X-axis labels
        lineChart.xAxis.setDrawGridLines(false)

        // Set appropriate labels for x-axis
        when (period) {
            PERIOD_WEEK -> {
                lineChart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                )
                lineChart.xAxis.labelCount = 7
            }
            PERIOD_MONTH -> {
                lineChart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    arrayOf("Week 1", "Week 2", "Week 3", "Week 4", "Week 5")
                )
                lineChart.xAxis.labelCount = 5
            }
            PERIOD_YEAR -> {
                lineChart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                )
                lineChart.xAxis.labelCount = 12
            }
        }

        // Configure legend with theme-aware text
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = getChartTextColor() // Theme-aware legend text

        // Set chart background based on theme
        lineChart.setBackgroundColor(getChartBackgroundColor())

        lineChart.animateX(1000)
        lineChart.invalidate()
    }


    // Updated setupEarningsLineChart function
    private fun setupEarningsLineChart(lineChart: LineChart, earnings: List<Earning>, period: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val entries = ArrayList<Entry>()
        val (startCalendar, endCalendar) = getDateRange(period)

        when (period) {
            PERIOD_WEEK -> {
                val dailyEarnings = Array(7) { 0f }

                earnings.forEach { earning ->
                    try {
                        val earningDate = dateFormat.parse(earning.date) ?: return@forEach
                        val earningCalendar = Calendar.getInstance().apply { time = earningDate }

                        val dayOfWeek = earningCalendar.get(Calendar.DAY_OF_WEEK)
                        val index = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

                        val amount = earning.amount.toFloat()
                        dailyEarnings[index] += amount
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (i in dailyEarnings.indices) {
                    entries.add(Entry(i.toFloat(), dailyEarnings[i]))
                }
            }
            PERIOD_MONTH -> {
                val weeklyEarnings = Array(5) { 0f }

                earnings.forEach { earning ->
                    try {
                        val earningDate = dateFormat.parse(earning.date) ?: return@forEach
                        val earningCalendar = Calendar.getInstance().apply { time = earningDate }

                        val weekOfMonth = earningCalendar.get(Calendar.WEEK_OF_MONTH) - 1
                        val index = weekOfMonth.coerceIn(0, 4)

                        val amount = earning.amount.toFloat()
                        weeklyEarnings[index] += amount
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (i in weeklyEarnings.indices) {
                    entries.add(Entry(i.toFloat(), weeklyEarnings[i]))
                }
            }
            PERIOD_YEAR -> {
                val monthlyEarnings = Array(12) { 0f }

                earnings.forEach { earning ->
                    try {
                        val earningDate = dateFormat.parse(earning.date) ?: return@forEach
                        val earningCalendar = Calendar.getInstance().apply { time = earningDate }

                        val month = earningCalendar.get(Calendar.MONTH)
                        val amount = earning.amount.toFloat()
                        monthlyEarnings[month] += amount
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (i in monthlyEarnings.indices) {
                    entries.add(Entry(i.toFloat(), monthlyEarnings[i]))
                }
            }
        }

        if (entries.isEmpty() || entries.all { it.y <= 0f }) {
            lineChart.setNoDataText("No earnings data available")
            lineChart.invalidate()
            return
        }

        val dataSet = LineDataSet(entries, "Earnings Trends")
        dataSet.color = getColor(requireContext(), R.color.progress)
        dataSet.valueTextColor = getChartTextColor() // Theme-aware value text color
        dataSet.valueTextSize = 10f
        dataSet.setCircleColor(getColor(requireContext(), R.color.progress))
        dataSet.circleRadius = 4f

        val lineData = LineData(dataSet)

        lineChart.data = lineData
        lineChart.description.isEnabled = false

        // Configure Y-axis with theme-aware text
        lineChart.axisLeft.axisMinimum = 0f
        lineChart.axisLeft.textColor = getChartTextColor() // Theme-aware Y-axis labels
        lineChart.axisRight.isEnabled = false // Disable right axis

        // Configure X-axis with theme-aware text
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.granularity = 1f
        lineChart.xAxis.textColor = getChartTextColor() // Theme-aware X-axis labels
        lineChart.xAxis.setDrawGridLines(false)

        when (period) {
            PERIOD_WEEK -> {
                lineChart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                )
                lineChart.xAxis.labelCount = 7
            }
            PERIOD_MONTH -> {
                lineChart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    arrayOf("Week 1", "Week 2", "Week 3", "Week 4", "Week 5")
                )
                lineChart.xAxis.labelCount = 5
            }
            PERIOD_YEAR -> {
                lineChart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                )
                lineChart.xAxis.labelCount = 12
            }
        }

        // Configure legend with theme-aware text
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = getChartTextColor() // Theme-aware legend text

        // Set chart background based on theme
        lineChart.setBackgroundColor(getChartBackgroundColor())

        lineChart.animateX(1000)
        lineChart.invalidate()
    }
}