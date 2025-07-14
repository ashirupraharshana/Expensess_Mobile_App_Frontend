package com.example.finbot.model

data class Earning(
    val id: String,
    val category: String,
    val amount: Double,
    val date: String,
    val time: String,
    val userId: String
)