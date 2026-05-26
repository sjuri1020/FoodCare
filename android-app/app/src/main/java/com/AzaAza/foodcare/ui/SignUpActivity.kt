package com.AzaAza.foodcare.ui

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.SignUpRequest
import com.AzaAza.foodcare.models.SignupResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        findViewById<TextView>(R.id.topBarTitle).text = "회원가입"
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        val etLoginId = findViewById<EditText>(R.id.etSignLoginId)
        val etUsername = findViewById<EditText>(R.id.etSignUsername)
        val etEmail = findViewById<EditText>(R.id.etSignEmail)
        val etPw = findViewById<EditText>(R.id.etSignPw)
        val etPwConfirm = findViewById<EditText>(R.id.etSignPwConfirm)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)

        btnSignUp.setOnClickListener {
            val loginId = etLoginId.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pw = etPw.text.toString().trim()
            val pwConfirm = etPwConfirm.text.toString().trim()

            when {
                loginId.isBlank() || username.isBlank() || email.isBlank() || pw.isBlank() -> {
                    Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
                pw != pwConfirm -> {
                    Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val request = SignUpRequest(login_id = loginId, username = username, email = email, password = pw)

                    RetrofitClient.userApiService.signUp(request).enqueue(object : Callback<SignupResponse> {
                        override fun onResponse(call: Call<SignupResponse>, response: Response<SignupResponse>) {
                            if (response.isSuccessful && response.body()?.success == true) {
                                Toast.makeText(this@SignUpActivity, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Log.e("SIGNUP_ERROR", response.errorBody()?.string() ?: "Unknown error")
                                Toast.makeText(this@SignUpActivity, "회원가입 실패", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<SignupResponse>, t: Throwable) {
                            Log.e("SIGNUP_FAILURE", "네트워크 오류: ${t.message}")
                            Toast.makeText(this@SignUpActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }
}