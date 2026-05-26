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

class FindIdActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etVerifyCode: EditText
    private lateinit var btnSendCode: Button
    private lateinit var btnFindId: Button

    private var isCodeSent: Boolean = false
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_id)

        // 툴바 세팅
        findViewById<TextView>(R.id.topBarTitle).text = "아이디 찾기"
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // 뷰 초기화
        etName = findViewById(R.id.etFindName)
        etEmail = findViewById(R.id.etFindEmail)
        etVerifyCode = findViewById(R.id.etVerifyCode)
        btnSendCode = findViewById(R.id.btnSendCode)
        btnFindId = findViewById(R.id.btnFindId)

        // 인증번호 받기 버튼 클릭 리스너
        btnSendCode.setOnClickListener {
            if (validateInput()) {
                requestVerificationCode()
            }
        }

        // 아이디 찾기 버튼 클릭 리스너
        btnFindId.setOnClickListener {
            if (isCodeSent) {
                verifyCode()
            } else {
                Toast.makeText(this, "먼저, 인증번호를 받으세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 입력 값 유효성 검사
    private fun validateInput(): Boolean {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        // 이메일 형식 검사 (간단한 정규식)
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
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
        loadingDialog = null
    }

    // 인증번호 요청 API 호출
    private fun requestVerificationCode() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        showLoading("인증번호를 요청 중입니다...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestBody = VerificationRequestDto(
                    name = name,
                    email = email,
                    purpose = "findId"
                )

                val response = RetrofitClient.userApiService.requestVerificationCode(requestBody)

                withContext(Dispatchers.Main) {
                    hideLoading()

                    if (response.success) {
                        Toast.makeText(this@FindIdActivity, response.message, Toast.LENGTH_SHORT).show()
                        isCodeSent = true
                        btnSendCode.text = "재전송"
                        // 성공적으로 인증번호를 받으면 인증번호 입력 필드에 포커스 주기
                        etVerifyCode.requestFocus()
                    } else {
                        // 서버에서 계정을 찾을 수 없다고 할 때
                        if (response.message.contains("계정을 찾을 수 없")) {
                            showAccountNotFoundDialog(email)
                        } else {
                            Toast.makeText(this@FindIdActivity, response.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    handleNetworkError(e, email)
                }
            }
        }
    }

    private fun handleNetworkError(e: Exception, email: String) {
        when (e) {
            is SocketTimeoutException -> {
                showRetryDialog("서버 응답 시간이 초과되었습니다. 다시 시도하시겠습니까?") {
                    requestVerificationCode()
                }
            }
            is IOException -> {
                showRetryDialog("네트워크 연결에 문제가 있습니다. 인터넷 연결을 확인하고 다시 시도하시겠습니까?") {
                    requestVerificationCode()
                }
            }
            is HttpException -> {
                if (e.code() == 500) {
                    showServerErrorDialog()
                } else {
                    Toast.makeText(this@FindIdActivity, "오류 코드: ${e.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(this@FindIdActivity, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRetryDialog(message: String, retryAction: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("연결 오류")
            .setMessage(message)
            .setPositiveButton("다시 시도") { _, _ -> retryAction() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showServerErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("서버 오류")
            .setMessage("서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.\n\n문제가 지속되면 고객센터에 문의해주세요.")
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showAccountNotFoundDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("계정을 찾을 수 없음")
            .setMessage("입력하신 이메일($email)로 가입된 계정을 찾을 수 없습니다.\n\n이메일 주소를 다시 확인하거나, 다른 이메일로 가입하셨을 수 있습니다.")
            .setPositiveButton("확인", null)
            .show()
    }

    // 인증번호 확인 API 호출
    private fun verifyCode() {
        val email = etEmail.text.toString().trim()
        val code = etVerifyCode.text.toString().trim()

        if (code.isEmpty()) {
            Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading("인증번호를 확인 중입니다...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestBody = VerificationConfirmDto(
                    email = email,
                    code = code,
                    purpose = "findId"
                )

                val response = RetrofitClient.userApiService.confirmVerificationCode(requestBody)

                withContext(Dispatchers.Main) {
                    hideLoading()

                    if (response.success) {
                        // 아이디 표시
                        val userId = response.data?.get("login_id") ?: "확인 불가"
                        showUserId(userId)
                    } else {
                        Toast.makeText(this@FindIdActivity, response.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    handleNetworkError(e, email)
                }
            }
        }
    }

    // 아이디 표시 다이얼로그
    private fun showUserId(userId: String) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("아이디 찾기 결과")
        dialogBuilder.setMessage("회원님의 아이디는 ${userId} 입니다.")
        dialogBuilder.setPositiveButton("확인") { dialog, _ ->
            dialog.dismiss()
            finish()  // 찾기 성공 후 화면 종료
        }
        dialogBuilder.setCancelable(false)
        dialogBuilder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 액티비티가 종료될 때 다이얼로그가 열려있으면 닫기
        loadingDialog?.dismiss()
        loadingDialog = null
    }
}