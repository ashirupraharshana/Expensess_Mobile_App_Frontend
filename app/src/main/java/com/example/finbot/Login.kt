package com.example.finbot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class Login : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailField = findViewById<EditText>(R.id.email)
        val passwordField = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.login)
        val registerButton = findViewById<Button>(R.id.register)
        val fpbtn = findViewById<TextView>(R.id.forgot_password)

        // Check if auto-fill data is passed from Register activity
        val autoFill = intent.getBooleanExtra("auto_fill", false)
        if (autoFill) {
            val email = intent.getStringExtra("email") ?: ""
            val password = intent.getStringExtra("password") ?: ""

            emailField.setText(email)
            passwordField.setText(password)

            // Show a toast indicating auto-fill
            Toast.makeText(this, "Registration successful! Please login.", Toast.LENGTH_SHORT).show()
        }

        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }

        fpbtn.setOnClickListener {
            startActivity(Intent(this, ForgetPassword::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.103.87:8082/api/users/login")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonRequest = JSONObject()
                jsonRequest.put("email", email)
                jsonRequest.put("password", password)

                val outputStream = DataOutputStream(conn.outputStream)
                outputStream.writeBytes(jsonRequest.toString())
                outputStream.flush()
                outputStream.close()

                val responseCode = conn.responseCode
                val inputStream: InputStream =
                    if (responseCode == 200) conn.inputStream else conn.errorStream
                val response = inputStream.bufferedReader().use { it.readText() }

                runOnUiThread {

                    try {
                        val jsonResponse = JSONObject(response)
                        val userId = jsonResponse.getString("id")
                        val username = jsonResponse.getString("username")

                        // Save to SharedPreferences
                        val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putBoolean("is_logged_in", true)
                            putString("email", email)
                            putString("user_id", userId)
                            putString("username", username)
                            apply()
                        }

                        Toast.makeText(this@Login, "Welcome $username!", Toast.LENGTH_LONG).show()

                        // Navigate to main screen
                        startActivity(Intent(this@Login, MainActivity::class.java))
                        finish()

                    } catch (e: Exception) {
                        Toast.makeText(this@Login, "Login failed: ${response}", Toast.LENGTH_LONG).show()
                    }

                }

                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@Login, "Login failed: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}