package com.example.finbot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val name = findViewById<EditText>(R.id.Name)
        val email = findViewById<EditText>(R.id.Email)
        val password = findViewById<EditText>(R.id.Password)
        val regbtn = findViewById<Button>(R.id.registerbtn)
        val logbtn = findViewById<Button>(R.id.loginbtn)

        regbtn.setOnClickListener {
            val uname = name.text.toString()
            val uemail = email.text.toString()
            val upass = password.text.toString()

            // Call API in background thread
            CoroutineScope(Dispatchers.IO).launch {
                sendRegisterRequest(uname, uemail, upass)
            }
        }
        logbtn.setOnClickListener{
            startActivity(Intent(this, Login::class.java))
        }
    }

    private fun sendRegisterRequest(username: String, email: String, password: String) {
        try {
            val url = URL("http://192.168.1.101:8082/api/users/register")
            val json = JSONObject().apply {
                put("username", username)
                put("email", email)
                put("password", password)
            }

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true

                // Send JSON payload
                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.write(json.toString())
                writer.flush()
                writer.close()

                // Read response
                val responseText = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

                runOnUiThread {
                    Toast.makeText(this@Register, responseText, Toast.LENGTH_LONG).show()
                    if (responseText.contains("success", ignoreCase = true)) {
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
                Toast.makeText(this@Register, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}