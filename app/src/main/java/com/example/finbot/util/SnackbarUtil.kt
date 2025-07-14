package com.example.finbot.util

import android.view.View
import com.google.android.material.snackbar.Snackbar
import android.graphics.Color
import android.widget.TextView

object SnackbarUtil {
    fun showSuccess(view: View, message: String, duration: Int = 3000) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.duration = duration
        snackbar.view.setBackgroundColor(Color.parseColor("#388E3C")) // Green
        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setTextColor(Color.WHITE)
        snackbar.show()
    }

    fun showWarning(view: View, message: String, duration: Int = 3000) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.duration = duration
        snackbar.view.setBackgroundColor(Color.parseColor("#FFA000")) // Amber
        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setTextColor(Color.BLACK)
        snackbar.show()
    }
} 