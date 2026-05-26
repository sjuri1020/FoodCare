package com.AzaAza.foodcare.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient

class SettingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { onBackPressed() }

        // 로그아웃
        findViewById<View>(R.id.logoutBar).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("로그아웃 하시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    performLogout()  // 이미 분리된 함수 있음
                }
                .setNegativeButton("아니오", null)
                .show()
        }


        // 프로필 설정
        findViewById<View>(R.id.profileSettingBar).setOnClickListener {
            startActivity(Intent(this, ProfileSettingActivity::class.java))
        }

        // 비밀번호 변경
        findViewById<View>(R.id.passwordChangeBar).setOnClickListener {
            startActivity(Intent(this, PasswordChangeActivity::class.java))
        }

        // 회원 탈퇴
        findViewById<View>(R.id.deleteAccountBar).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("회원 탈퇴")
                .setMessage("정말 탈퇴하시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    deleteAccount()
                }
                .setNegativeButton("아니오", null)
                .show()
        }

        // 서비스 이용약관
        findViewById<View>(R.id.termsServiceBar).setOnClickListener {
            Toast.makeText(this, "추후 추가될 예정입니다.", Toast.LENGTH_SHORT).show()
        }

        // 개인정보 처리방침
        findViewById<View>(R.id.privacyPolicyBar).setOnClickListener {
            Toast.makeText(this, "추후 추가될 예정입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 로그아웃 처리 함수 분리
    private fun performLogout() {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }


    private fun deleteAccount() {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val loginId = prefs.getString("USER_LOGIN_ID", null)

        if (loginId == null) {
            Toast.makeText(this, "로그인 정보 없음", Toast.LENGTH_SHORT).show()
            return
        }

        // 서버에 회원 삭제 요청
        RetrofitClient.userApiService.deleteUser(loginId)
            .enqueue(object : retrofit2.Callback<Void> {
                override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SettingActivity, "회원 탈퇴되었습니다.", Toast.LENGTH_SHORT).show()
                        prefs.edit().clear().apply()
                        val intent = Intent(this@SettingActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@SettingActivity, "서버 오류: 탈퇴 실패", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                    Toast.makeText(this@SettingActivity, "통신 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }


    private fun openWebPage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressed(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}