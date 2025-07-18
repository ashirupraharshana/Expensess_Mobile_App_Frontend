package com.example.finbot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class Register : AppCompatActivity() {

    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var otpField: EditText
    private lateinit var sendOtpBtn: Button
    private lateinit var registerBtn: Button
    private lateinit var loginBtn: Button
    private lateinit var otpLayout: TextInputLayout
    private lateinit var nameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout

    private var isOtpSent = false
    private var isOtpVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize views
        nameField = findViewById(R.id.Name)
        emailField = findViewById(R.id.Email)
        passwordField = findViewById(R.id.Password)
        otpField = findViewById(R.id.Otp)
        sendOtpBtn = findViewById(R.id.sentotpbtn)
        registerBtn = findViewById(R.id.registerbtn)
        loginBtn = findViewById(R.id.loginbtn)

        // Initialize layouts
        otpLayout = findViewById(R.id.Otptextview)
        nameLayout = findViewById(R.id.nameInputLayout)
        passwordLayout = findViewById(R.id.passwordInputLayout)

        // Initially hide OTP field and other fields
        hideRegistrationFields()

        // Set up click listeners
        sendOtpBtn.setOnClickListener {
            val email = emailField.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isOtpSent) {
                // Send OTP
                sendOtpBtn.isEnabled = false
                sendOtpBtn.text = "Sending..."
                CoroutineScope(Dispatchers.IO).launch {
                    sendOtpRequest(email)
                }
            } else {
                // Verify OTP
                val otpCode = otpField.text.toString().trim()
                if (otpCode.isEmpty()) {
                    Toast.makeText(this, "Please enter OTP", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                sendOtpBtn.isEnabled = false
                sendOtpBtn.text = "Verifying..."
                CoroutineScope(Dispatchers.IO).launch {
                    verifyOtpRequest(email, otpCode)
                }
            }
        }

        registerBtn.setOnClickListener {
            if (!isOtpVerified) {
                Toast.makeText(this, "Please verify OTP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerBtn.isEnabled = false
            registerBtn.text = "Registering..."
            CoroutineScope(Dispatchers.IO).launch {
                sendRegisterRequest(name, email, password)
            }
        }

        loginBtn.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }
    }

    private fun hideRegistrationFields() {
        otpLayout.visibility = View.GONE
        nameLayout.visibility = View.GONE
        passwordLayout.visibility = View.GONE
        registerBtn.visibility = View.GONE
    }

    private fun showOtpField() {
        otpLayout.visibility = View.VISIBLE
    }

    private fun showRegistrationFields() {
        nameLayout.visibility = View.VISIBLE
        passwordLayout.visibility = View.VISIBLE
        registerBtn.visibility = View.VISIBLE
    }

    private fun sendOtpRequest(email: String) {
        try {
            val url = URL("http://192.168.1.100:8082/api/otp/send/registration")
            val json = JSONObject().apply {
                put("email", email)
            }

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true

                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.write(json.toString())
                writer.flush()
                writer.close()

                val responseCode = responseCode
                val responseText = if (responseCode == 200) {
                    BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                }

                runOnUiThread {
                    sendOtpBtn.isEnabled = true
                    if (responseCode == 200) {
                        // Check if response contains "already exists" message
                        if (responseText.contains("already exists", ignoreCase = true)) {
                            Toast.makeText(this@Register, "Email already registered!", Toast.LENGTH_LONG).show()
                            sendOtpBtn.text = "Send OTP"
                        } else {
                            Toast.makeText(this@Register, "OTP sent successfully!", Toast.LENGTH_SHORT).show()
                            isOtpSent = true
                            showOtpField()
                            sendOtpBtn.text = "Verify OTP"
                            emailField.isEnabled = false // Disable email field after OTP is sent
                        }
                    } else {
                        Toast.makeText(this@Register, responseText, Toast.LENGTH_LONG).show()
                        sendOtpBtn.text = "Send OTP"
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                sendOtpBtn.isEnabled = true
                sendOtpBtn.text = "Send OTP"
                Toast.makeText(this@Register, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun verifyOtpRequest(email: String, otpCode: String) {
        try {
            val url = URL("http://192.168.1.100:8082/api/otp/verify")
            val json = JSONObject().apply {
                put("email", email)
                put("otpCode", otpCode)
                put("purpose", "REGISTRATION")
            }

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true

                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.write(json.toString())
                writer.flush()
                writer.close()

                val responseCode = responseCode
                val responseText = if (responseCode == 200) {
                    BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                }

                runOnUiThread {
                    sendOtpBtn.isEnabled = true
                    if (responseCode == 200) {
                        Toast.makeText(this@Register, "OTP verified successfully!", Toast.LENGTH_SHORT).show()
                        isOtpVerified = true
                        showRegistrationFields()
                        sendOtpBtn.visibility = View.GONE
                        otpLayout.visibility = View.GONE // Hide OTP field after verification
                    } else {
                        Toast.makeText(this@Register, responseText, Toast.LENGTH_LONG).show()
                        sendOtpBtn.text = "Verify OTP"
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                sendOtpBtn.isEnabled = true
                sendOtpBtn.text = "Verify OTP"
                Toast.makeText(this@Register, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendRegisterRequest(username: String, email: String, password: String) {
        try {
            val url = URL("http://192.168.1.100:8082/api/users/register")
            val json = JSONObject().apply {
                put("username", username)
                put("email", email)
                put("password", password)
            }

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true

                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.write(json.toString())
                writer.flush()
                writer.close()

                val responseCode = responseCode
                val responseText = if (responseCode == 200) {
                    BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                }

                runOnUiThread {
                    registerBtn.isEnabled = true
                    registerBtn.text = "Register"
                    Toast.makeText(this@Register, responseText, Toast.LENGTH_LONG).show()

                    if (responseCode == 200 && responseText.contains("success", ignoreCase = true)) {
                        // Navigate to login page with auto-fill data
                        val intent = Intent(this@Register, Login::class.java)
                        intent.putExtra("email", email)
                        intent.putExtra("password", password)
                        intent.putExtra("auto_fill", true)
                        startActivity(intent)
                        finish() // Close register activity
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                registerBtn.isEnabled = true
                registerBtn.text = "Register"
                Toast.makeText(this@Register, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}