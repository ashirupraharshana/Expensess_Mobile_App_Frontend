package com.example.finbot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

class ForgetPassword : AppCompatActivity() {

    private lateinit var emailField: EditText
    private lateinit var newPasswordField: EditText
    private lateinit var confirmPasswordField: EditText
    private lateinit var sendOtpBtn: Button
    private lateinit var submitBtn: Button
    private lateinit var loginBtn: TextView


    private lateinit var newPasswordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout

    private var isOtpSent = false
    private var isOtpVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forget_password)

        // Initialize views
        emailField = findViewById(R.id.Email)
        newPasswordField = findViewById(R.id.Password2)
        confirmPasswordField = findViewById(R.id.Password3)
        sendOtpBtn = findViewById(R.id.sentotpbtn)
        submitBtn = findViewById(R.id.submitbtn)
        loginBtn = findViewById(R.id.loginbtn)

        // Initialize layouts
        newPasswordLayout = findViewById(R.id.passwordInputLayout2)
        confirmPasswordLayout = findViewById(R.id.passwordInputLayout3)

        // Initially hide password fields and submit button
        hidePasswordFields()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.forgetpw)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set up click listeners
        sendOtpBtn.setOnClickListener {
            val email = emailField.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isEmailValid(email)) {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendOtpBtn.isEnabled = false
            sendOtpBtn.text = "Sending..."
            CoroutineScope(Dispatchers.IO).launch {
                sendPasswordResetOtp(email)
            }
        }

        submitBtn.setOnClickListener {
            if (!isOtpVerified) {
                Toast.makeText(this, "Please verify OTP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val email = emailField.text.toString().trim()
            val newPassword = newPasswordField.text.toString().trim()
            val confirmPassword = confirmPasswordField.text.toString().trim()

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all password fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitBtn.isEnabled = false
            submitBtn.text = "Updating..."
            CoroutineScope(Dispatchers.IO).launch {
                updatePassword(email, newPassword)
            }
        }

        loginBtn.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }

    private fun hidePasswordFields() {
        newPasswordLayout.visibility = View.GONE
        confirmPasswordLayout.visibility = View.GONE
        submitBtn.visibility = View.GONE
    }

    private fun showPasswordFields() {
        newPasswordLayout.visibility = View.VISIBLE
        confirmPasswordLayout.visibility = View.VISIBLE
        submitBtn.visibility = View.VISIBLE
    }

    private fun resetForm() {
        emailField.isEnabled = true
        emailField.text.clear()
        newPasswordField.text.clear()
        confirmPasswordField.text.clear()
        sendOtpBtn.text = "Send OTP"
        sendOtpBtn.isEnabled = true
        submitBtn.text = "Submit"
        submitBtn.isEnabled = true
        hidePasswordFields()
        isOtpSent = false
        isOtpVerified = false
    }

    private fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun sendPasswordResetOtp(email: String) {
        try {
            val url = URL("http://192.168.103.87:8082/api/otp/send/password-reset")
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
                        if (responseText.contains("User not found", ignoreCase = true)) {
                            Toast.makeText(this@ForgetPassword, "Email not registered!", Toast.LENGTH_LONG).show()
                            sendOtpBtn.text = "Send OTP"
                        } else {
                            Toast.makeText(this@ForgetPassword, "OTP sent successfully to your email!", Toast.LENGTH_SHORT).show()
                            isOtpSent = true
                            // Start OTP verification process
                            startOtpVerification(email)
                        }
                    } else {
                        Toast.makeText(this@ForgetPassword, responseText, Toast.LENGTH_LONG).show()
                        sendOtpBtn.text = "Send OTP"
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                sendOtpBtn.isEnabled = true
                sendOtpBtn.text = "Send OTP"
                Toast.makeText(this@ForgetPassword, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startOtpVerification(email: String) {
        // Show OTP input dialog
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_otp_input, null)

        val otpInput = dialogView.findViewById<EditText>(R.id.otpInput)
        val verifyBtn = dialogView.findViewById<Button>(R.id.verifyBtn)
        val resendBtn = dialogView.findViewById<Button>(R.id.resendBtn)

        val dialog = builder.setView(dialogView)
            .setTitle("Enter OTP")
            .setMessage("Please enter the OTP sent to your email")
            .setCancelable(false)
            .create()

        verifyBtn.setOnClickListener {
            val otpCode = otpInput.text.toString().trim()
            if (otpCode.isEmpty()) {
                Toast.makeText(this, "Please enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            verifyBtn.isEnabled = false
            verifyBtn.text = "Verifying..."
            CoroutineScope(Dispatchers.IO).launch {
                verifyOtp(email, otpCode, dialog)
            }
        }

        resendBtn.setOnClickListener {
            resendBtn.isEnabled = false
            resendBtn.text = "Resending..."
            CoroutineScope(Dispatchers.IO).launch {
                resendOtp(email, resendBtn)
            }
        }

        dialog.show()
    }

    private fun verifyOtp(email: String, otpCode: String, dialog: androidx.appcompat.app.AlertDialog) {
        try {
            val url = URL("http://192.168.103.87:8082/api/otp/verify")
            val json = JSONObject().apply {
                put("email", email)
                put("otpCode", otpCode)
                put("purpose", "PASSWORD_RESET")
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
                    if (responseCode == 200) {
                        Toast.makeText(this@ForgetPassword, "OTP verified successfully!", Toast.LENGTH_SHORT).show()
                        isOtpVerified = true
                        dialog.dismiss()
                        emailField.isEnabled = false
                        sendOtpBtn.visibility = View.GONE
                        showPasswordFields()
                    } else {
                        Toast.makeText(this@ForgetPassword, "Invalid or expired OTP", Toast.LENGTH_LONG).show()
                        val verifyBtn = dialog.findViewById<Button>(R.id.verifyBtn)
                        verifyBtn?.isEnabled = true
                        verifyBtn?.text = "Verify OTP"
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this@ForgetPassword, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                val verifyBtn = dialog.findViewById<Button>(R.id.verifyBtn)
                verifyBtn?.isEnabled = true
                verifyBtn?.text = "Verify OTP"
            }
        }
    }

    private fun resendOtp(email: String, resendBtn: Button) {
        try {
            val url = URL("http://192.168.103.87:8082/api/otp/resend")
            val json = JSONObject().apply {
                put("email", email)
                put("purpose", "PASSWORD_RESET")
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
                    resendBtn.isEnabled = true
                    resendBtn.text = "Resend OTP"
                    if (responseCode == 200) {
                        Toast.makeText(this@ForgetPassword, "OTP resent successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ForgetPassword, responseText, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                resendBtn.isEnabled = true
                resendBtn.text = "Resend OTP"
                Toast.makeText(this@ForgetPassword, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updatePassword(email: String, newPassword: String) {
        try {
            // First, we need to get the user and then update the password
            // Since your backend doesn't have a dedicated password reset endpoint,
            // we'll need to create one or use a workaround

            // For now, let's assume we have a password update endpoint
            // You'll need to add this to your backend
            val url = URL("http://192.168.103.87:8082/api/users/update-password")
            val json = JSONObject().apply {
                put("email", email)
                put("newPassword", newPassword)
            }

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "PUT"
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
                    submitBtn.isEnabled = true
                    submitBtn.text = "Submit"

                    if (responseCode == 200) {
                        Toast.makeText(this@ForgetPassword, "Password updated successfully!", Toast.LENGTH_LONG).show()

                        // Navigate to login with the new credentials
                        val intent = Intent(this@ForgetPassword, Login::class.java)
                        intent.putExtra("email", email)
                        intent.putExtra("password", newPassword)
                        intent.putExtra("auto_fill", true)
                        intent.putExtra("password_reset", true)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@ForgetPassword, "Failed to update password: $responseText", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                submitBtn.isEnabled = true
                submitBtn.text = "Submit"
                Toast.makeText(this@ForgetPassword, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}