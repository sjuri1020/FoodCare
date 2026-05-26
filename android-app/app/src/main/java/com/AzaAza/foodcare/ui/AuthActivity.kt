package com.AzaAza.foodcare.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // SharedPreferences에서 로그인 여부 확인
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("IS_LOGGED_IN", false)
        Log.d("AuthActivity", "isLoggedIn = $isLoggedIn")


        if (isLoggedIn) {
            // 이미 로그인된 상태이면 MainActivity로
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // 로그인 필요하면 LoginActivity로
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}