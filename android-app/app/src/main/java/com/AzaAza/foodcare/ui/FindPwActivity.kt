package com.AzaAza.foodcare.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.VerificationRequestDto
import com.AzaAza.foodcare.models.VerificationConfirmDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import retrofit2.HttpException
import java.io.IOException

class FindPwActivity : AppCompatActivity() {

    private lateinit var etLoginId: EditText
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etVerifyCode: EditText
    private lateinit var btnSendCode: Button
    private lateinit var btnFindPw: Button

    private var isCodeSent: Boolean = false
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_pw)

        // 툴바 세팅
        findViewById<TextView>(R.id.topBarTitle).text = "비밀번호 찾기"
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // 뷰 초기화
        etLoginId = findViewById(R.id.etFindLoginId)
        etName = findViewById(R.id.etFindName)
        etEmail = findViewById(R.id.etFindEmail)
        etVerifyCode = findViewById(R.id.etVerifyCode)
        btnSendCode = findViewById(R.id.btnSendCode)
        btnFindPw = findViewById(R.id.btnFindPw)

        // 인증번호 받기 버튼 클릭 리스너
        btnSendCode.setOnClickListener {
            if (validateInputForVerification()) {
                requestVerificationCode()
            }
        }

        // 비밀번호 찾기 버튼 클릭 리스너
        btnFindPw.setOnClickListener {
            if (validateInputForReset()) {
                verifyCodeAndResetPassword()
            }
        }
    }

    // 인증번호 요청 전 입력 검증
    private fun validateInputForVerification(): Boolean {
        val loginId = etLoginId.text.toString().trim()
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (loginId.isEmpty()) {
            Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        // 이메일 형식 검사
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // 비밀번호 찾기 전 입력 검증
    private fun validateInputForReset(): Boolean {
        if (!isCodeSent) {
            Toast.makeText(this, "먼저 인증번호를 요청하세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        val code = etVerifyCode.text.toString().trim()
        if (code.isEmpty()) {
            Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (code.length != 6) {
            Toast.makeText(this, "인증번호는 6자리입니다.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun showLoading(message: String) {
        if (loadingDialog?.isShowing == true) {
            loadingDialog?.dismiss()
        }

        loadingDialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
    }

    // 인증번호 요청 API 호출
    private fun requestVerificationCode() {
        val loginId = etLoginId.text.toString().trim()
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        showLoading("인증번호를 요청 중입니다...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestBody = VerificationRequestDto(
                    name = name,
                    email = email,
                    purpose = "resetPassword",
                    login_id = loginId
                )

                val response = RetrofitClient.userApiService.requestVerificationCode(requestBody)

                withContext(Dispatchers.Main) {
                    hideLoading()

                    if (response.success) {
                        isCodeSent = true
                        btnSendCode.text = "재전송"
                        Toast.makeText(this@FindPwActivity, response.message, Toast.LENGTH_SHORT).show()
                        etVerifyCode.requestFocus()
                    } else {
                        // 사용자 정보 불일치 시
                        if (response.message.contains("일치하는 계정을 찾을 수 없습니다")) {
                            showAccountNotFoundDialog()
                        } else {
                            Toast.makeText(this@FindPwActivity, response.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    handleNetworkError(e)
                }
            }
        }
    }

    // 인증번호 확인 및 비밀번호 재설정 API 호출
    private fun verifyCodeAndResetPassword() {
        val email = etEmail.text.toString().trim()
        val code = etVerifyCode.text.toString().trim()

        showLoading("인증번호를 확인 중입니다...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestBody = VerificationConfirmDto(
                    email = email,
                    code = code,
                    purpose = "resetPassword"
                )

                val response = RetrofitClient.userApiService.confirmVerificationCode(requestBody)

                withContext(Dispatchers.Main) {
                    hideLoading()

                    if (response.success) {
                        // 인증 완료 후 비밀번호 정보 표시
                        if (response.data != null && response.data.containsKey("password")) {
                            showPasswordInfo(response.data["password"] ?: "")
                        } else {
                            Toast.makeText(this@FindPwActivity, "비밀번호 정보를 받아오지 못했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@FindPwActivity, response.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    handleNetworkError(e)
                }
            }
        }
    }

    // 네트워크 오류 처리
    private fun handleNetworkError(e: Exception) {
        when (e) {
            is SocketTimeoutException -> {
                Toast.makeText(this, "서버 응답 시간이 초과되었습니다.", Toast.LENGTH_SHORT).show()
            }
            is IOException -> {
                Toast.makeText(this, "네트워크 연결에 문제가 있습니다.", Toast.LENGTH_SHORT).show()
            }
            is HttpException -> {
                Toast.makeText(this, "오류 코드: ${e.code()}", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 계정 정보 불일치 다이얼로그
    private fun showAccountNotFoundDialog() {
        AlertDialog.Builder(this)
            .setTitle("계정 정보 불일치")
            .setMessage("입력하신 정보와 일치하는 계정을 찾을 수 없습니다. 아이디, 이름, 이메일을 정확히 입력했는지 확인해주세요.")
            .setPositiveButton("확인", null)
            .show()
    }

    // 비밀번호 정보 다이얼로그
    private fun showPasswordInfo(password: String) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("비밀번호 찾기 결과")
        dialogBuilder.setMessage("회원님의 임시 비밀번호는 ${password}입니다.\n\n로그인 후 반드시 비밀번호를 변경해주세요.")
        dialogBuilder.setPositiveButton("확인") { dialog, _ ->
            dialog.dismiss()
            finish()
        }
        dialogBuilder.setCancelable(false)
        dialogBuilder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 액티비티가 종료될 때 다이얼로그가 열려있으면 닫기
        loadingDialog?.dismiss()
    }
}