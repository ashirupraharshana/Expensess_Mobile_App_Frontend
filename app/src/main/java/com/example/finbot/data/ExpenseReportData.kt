package com.example.finbot.data

data class ExpenseReportData(
    val totalExpenses: Double,
    val budgetLimit: Double,
    val budgetPercentage: Double,
    val budgetLimitValue: Double,
    val highestUsedCategory: String,
    val currencyType: String,
    val username: String,
    val email: String,
    val mostExpensesValuePerDay: Double,
    val dailyAverageValue: Double,
    val expenses: List<ExpenseItem>
)

data class ExpenseItem(
    val id: String,
    val name: String,
    val categoryId: Int,
    val categoryName: String,
    val date: String,
    val time: String,
    val amount: Double,
    val userId: String
)