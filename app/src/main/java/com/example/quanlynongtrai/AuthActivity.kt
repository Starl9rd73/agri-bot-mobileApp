package com.example.quanlynongtrai

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        dbHelper = DatabaseHelper(this)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnAction = findViewById<Button>(R.id.btnAction)
        val tvToggleMode = findViewById<TextView>(R.id.tvToggleMode)

        tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            if (isLoginMode) {
                tvTitle.text = "Chào mừng trở lại"
                btnAction.text = "Đăng nhập"
                tvToggleMode.text = "Chưa có tài khoản? Đăng ký"
            } else {
                tvTitle.text = "Tạo tài khoản mới"
                btnAction.text = "Đăng ký"
                tvToggleMode.text = "Đã có tài khoản? Đăng nhập"
            }
        }

        btnAction.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isLoginMode) {
                // Logic Đăng nhập
                if (dbHelper.checkUser(email, password)) {
                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Email hoặc mật khẩu không đúng", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Logic Đăng ký
                if (dbHelper.isEmailExists(email)) {
                    Toast.makeText(this, "Email này đã được đăng ký", Toast.LENGTH_SHORT).show()
                } else {
                    val result = dbHelper.registerUser(email, password)
                    if (result != -1L) {
                        Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                        // Sau khi đăng ký, chuyển về chế độ đăng nhập hoặc tự động đăng nhập
                        isLoginMode = true
                        tvTitle.text = "Chào mừng trở lại"
                        btnAction.text = "Đăng nhập"
                        tvToggleMode.text = "Chưa có tài khoản? Đăng ký"
                    } else {
                        Toast.makeText(this, "Đăng ký thất bại, vui lòng thử lại", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}