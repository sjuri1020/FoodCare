package com.AzaAza.foodcare.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore

import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.ProfileImageResponse
import com.AzaAza.foodcare.models.SignUpRequest
import com.bumptech.glide.Glide

import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat



class ProfileSettingActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    private lateinit var profileImageView: ImageView
    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText

    private var selectedImageUri: Uri? = null
    private var currentPhotoPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setting)

        profileImageView = findViewById(R.id.profileImage)
        nameEditText = findViewById(R.id.inputName)
        emailEditText = findViewById(R.id.inputEmail)

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        profileImageView.setOnClickListener {
            showImagePickOptions()
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val name = nameEditText.text.toString()
            val email = emailEditText.text.toString()
            Toast.makeText(this, "저장됨: $name / $email", Toast.LENGTH_SHORT).show()
            // TODO: 저장 처리
            uploadProfileImage()
        }

        loadUserInfo()
    }
    // --- 사진 선택 및 촬영 구현 ---
    private fun showImagePickOptions() {
        val options = arrayOf("갤러리에서 선택", "카메라로 촬영")
        android.app.AlertDialog.Builder(this)
            .setTitle("프로필 사진 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageFromGallery()
                    1 -> checkCameraPermissionAndTakePicture()
                }
            }.show()
    }
    private fun copyUriToTempFile(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "profile_gallery_${System.currentTimeMillis()}.jpg")
        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile.absolutePath
    }

    // 갤러리에서 이미지 선택
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri: Uri? = result.data?.data
            uri?.let {
                selectedImageUri = it
                // 임시 파일로 복사
                currentPhotoPath = copyUriToTempFile(it)
                profileImageView.setImageURI(it)
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }
    // 카메라 촬영 후 저장
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val file = saveBitmapToFile(it)
            currentPhotoPath = file.absolutePath
            selectedImageUri = Uri.fromFile(file)
            profileImageView.setImageBitmap(it)
        }
    }

    private fun takePictureFromCamera() {
        cameraLauncher.launch(null)
    }

    // --- 실제 파일 경로 얻기 (갤러리 이미지) ---
    private fun getRealPathFromUri(uri: Uri): String {
        var path = ""
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri, proj, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = cursor.getString(column_index)
            }
            cursor.close()
        }
        return path
    }

    // --- 비트맵(카메라) 파일로 저장 ---
    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "profile_${System.currentTimeMillis()}.jpg")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.flush()
        out.close()
        return file
    }


    private fun uploadProfileImage() {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val loginId = prefs.getString("USER_LOGIN_ID", null) ?: return

        if (selectedImageUri != null && currentPhotoPath.isNotEmpty()) {
            val file = File(currentPhotoPath)
            val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", file.name, reqFile)
            val loginIdPart = loginId.toRequestBody("text/plain".toMediaTypeOrNull())

            RetrofitClient.userApiService.uploadProfileImage(loginIdPart, imagePart)
                .enqueue(object : Callback<ProfileImageResponse> {
                    override fun onResponse(call: Call<ProfileImageResponse>, response: Response<ProfileImageResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(this@ProfileSettingActivity, "프로필 사진이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            // 새로 저장된 사진 Glide로 반영하기
                            response.body()?.image_url?.let { imageUrl ->
                                Glide.with(this@ProfileSettingActivity)
                                    .load("https://foodcare-69ae76eec1bf.herokuapp.com$imageUrl")
                                    .circleCrop()
                                    .into(profileImageView)

                            }
                        } else {
                            Toast.makeText(this@ProfileSettingActivity, "저장 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<ProfileImageResponse>, t: Throwable) {
                        Toast.makeText(this@ProfileSettingActivity, "서버 오류", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }



    private fun loadUserInfo() {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val loginId = prefs.getString("USER_LOGIN_ID", null)

        if (loginId == null) {
            Toast.makeText(this, "로그인 정보 없음", Toast.LENGTH_SHORT).show()
            return
        }

        RetrofitClient.userApiService.getUserListAsSignUpRequest()
            .enqueue(object : Callback<List<SignUpRequest>> {
                override fun onResponse(
                    call: Call<List<SignUpRequest>>,
                    response: Response<List<SignUpRequest>>
                ) {
                    if (response.isSuccessful) {
                        val user = response.body()?.find { it.login_id == loginId }
                        if (user != null) {
                            nameEditText.setText(user.username)
                            emailEditText.setText(user.email)

                            if (!user.profile_image_url.isNullOrBlank()) {
                                Glide.with(this@ProfileSettingActivity)
                                    .load("https://foodcare-69ae76eec1bf.herokuapp.com${user.profile_image_url}")
                                    .circleCrop()
                                    .into(profileImageView)
                            } else {
                                profileImageView.setImageResource(R.drawable.ic_profile)
                            }

                        } else {
                            Toast.makeText(this@ProfileSettingActivity, "사용자 정보 없음", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@ProfileSettingActivity, "서버 오류", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<SignUpRequest>>, t: Throwable) {
                    Toast.makeText(this@ProfileSettingActivity, "통신 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
    private fun checkCameraPermissionAndTakePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            takePictureFromCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePictureFromCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }


}