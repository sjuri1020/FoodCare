package com.AzaAza.foodcare.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.IngredientDto
import com.AzaAza.foodcare.models.IngredientResponse
import com.AzaAza.foodcare.models.MemberResponse
import com.AzaAza.foodcare.data.UserSession
import com.AzaAza.foodcare.models.MyGroupsResponse
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import com.bumptech.glide.Glide

class FoodManagementActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val ingredientsList = mutableListOf<Ingredient>()
    private val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA)
    private val displayDateFormat = SimpleDateFormat("MM월 dd일", Locale.KOREA)
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 카메라 및 갤러리 관련 변수
    private val CAMERA_PERMISSION_CODE = 100
    private val STORAGE_PERMISSION_CODE = 101
    private val CAMERA_REQUEST_CODE = 102
    private val GALLERY_REQUEST_CODE = 103
    private var currentPhotoPath: String = ""
    private var currentPhotoUri: Uri? = null
    private var selectedImageBitmap: Bitmap? = null
    private lateinit var imageViewPhoto: ImageView
    private lateinit var currentDialog: Dialog

    // ID 매핑을 위한 Map
    private val ingredientIdMap = mutableMapOf<String, Int>() // 키: 이름+위치+날짜, 값: 서버ID

    // SharedPreferences 관련 상수 및 변수
    private lateinit var sharedPreferences: SharedPreferences
    private val PREF_NAME = "FoodcarePrefs"
    private val LAST_ALERT_DATE = "last_alert_date"

    // 현재 로그인한 사용자 ID
    private var currentUserId: Int = -1

    // 모드 관련 변수
    private var isSharedMode = false
    private var currentGroupOwnerId: Int = -1  // 현재 그룹의 대표자 ID
    private var currentGroupOwnerName: String = "" // 현재 그룹의 대표자 이름
    private lateinit var toggleModeButton: Button
    private lateinit var modeIndicator: TextView
    private lateinit var modeDescription: TextView

    data class Ingredient(
        val name: String,
        val location: String,
        val expiryDate: Date,
        val purchaseDate: Date,
        val imagePath: String? = null,   // 로컬 저장용
        val imageUrl: String? = null,    // 서버에서 받은 url 저장용
        val ownerId: Int = -1,           // 식자재 소유자 ID 추가
        val ownerName: String? = null    // 식자재 소유자 이름 추가
    ) {
        // 고유 식별자 생성
        fun getUniqueKey(): String {
            return "$name-$location-${expiryDate.time}-$ownerId"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food_management)

        // 현재 사용자 ID 가져오기
        currentUserId = UserSession.getUserId(this)

        // 로그인 상태 확인
        if (currentUserId == -1 || !UserSession.isLoggedIn(this)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // UI 요소 초기화
        initializeViews()
        initializeUI()

        // 앱 시작 시 서버에서 데이터 불러오기
        fetchIngredientsFromServer()

        // 검색 기능 구현
        setupSearchFunction()

        // 뒤로가기 버튼 설정
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }
    }

    private fun initializeViews() {
        container = findViewById<LinearLayout>(R.id.ingredientsContainer)
        toggleModeButton = findViewById<Button>(R.id.toggleModeButton)
        modeIndicator = findViewById<TextView>(R.id.modeIndicator)
        modeDescription = findViewById<TextView>(R.id.modeDescription)

        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            showAddIngredientDialog()
        }
    }

    private fun initializeUI() {
        setupModeToggle()
        updateModeUI()
    }

    private fun setupModeToggle() {
        toggleModeButton.setOnClickListener {
            if (isSharedMode) {
                // 공유 모드 → 개인 모드
                isSharedMode = false
                currentGroupOwnerName = ""  // 초기화
                updateModeUI()
                fetchIngredientsFromServer()
            } else {
                // 개인 모드 → 공유 모드
                isSharedMode = true
                // UI를 먼저 로딩 상태로 업데이트
                updateModeUILoading()
                fetchSharedIngredientsFromServer()
            }
        }
    }

    private fun updateModeUILoading() {
        // 공유 모드 전환 중 로딩 상태 표시
        modeIndicator.text = "공유 모드"
        modeIndicator.setTextColor(Color.parseColor("#FF9800"))
        modeIndicator.setBackgroundResource(R.drawable.mode_indicator_shared)

        toggleModeButton.text = "개인 모드로 전환"
        toggleModeButton.setBackgroundResource(R.drawable.mode_toggle_button)
        toggleModeButton.isEnabled = false  // 로딩 중 버튼 비활성화

        // 로딩 중 표시
        modeDescription.text = "그룹 정보 불러오는 중..."
        modeDescription.visibility = View.VISIBLE
    }

    private fun updateModeUI() {
        if (isSharedMode) {
            // 공유 모드로 전환
            modeIndicator.text = "공유 모드"
            modeIndicator.setTextColor(Color.parseColor("#FF9800"))
            modeIndicator.setBackgroundResource(R.drawable.mode_indicator_shared)

            toggleModeButton.text = "개인 모드로 전환"
            toggleModeButton.setBackgroundResource(R.drawable.mode_toggle_button)
            toggleModeButton.isEnabled = true  // 버튼 활성화

            // 대표자 이름이 있으면 표시, 없으면 기본값
            val groupDescription = if (currentGroupOwnerName.isNotEmpty()) {
                "${currentGroupOwnerName}님의 그룹 (공유 모드)"
            } else {
                "공유 그룹 (공유 모드)"
            }
            modeDescription.text = groupDescription
            modeDescription.visibility = View.VISIBLE

        } else {
            // 개인 모드로 전환
            modeIndicator.text = "개인 모드"
            modeIndicator.setTextColor(Color.parseColor("#4CAF50"))
            modeIndicator.setBackgroundResource(R.drawable.mode_indicator_personal)

            toggleModeButton.text = "공유 모드로 전환"
            toggleModeButton.setBackgroundResource(R.drawable.mode_toggle_button)
            toggleModeButton.isEnabled = true  // 버튼 활성화

            // 공유 모드 설명 텍스트 숨김
            modeDescription.visibility = View.GONE
        }
    }

    private fun setupSearchFunction() {
        val searchEditText = findViewById<EditText>(R.id.editTextSearch)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterIngredients(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // 현재 사용자의 식자재만 서버에서 가져오기
    private fun fetchIngredientsFromServer() {
        Log.d("FoodManagement", "사용자 $currentUserId 의 식자재 목록 가져오기 시작")

        // 사용자별 식자재 조회 API 호출
        RetrofitClient.ingredientApiService.getIngredients(currentUserId)
            .enqueue(object : Callback<List<IngredientDto>> {
                override fun onResponse(call: Call<List<IngredientDto>>, response: Response<List<IngredientDto>>) {
                    if (response.isSuccessful) {
                        val serverIngredients = response.body()
                        Log.d("FoodManagement", "사용자 $currentUserId 의 ${serverIngredients?.size ?: 0}개 식자재 데이터 수신")

                        if (serverIngredients != null) {
                            processServerIngredients(serverIngredients)
                        }
                    } else {
                        val errorMsg = response.errorBody()?.string() ?: "알 수 없는 오류"
                        Log.e("FoodManagement", "서버 응답 오류: ${response.code()}, $errorMsg")
                        showToast("서버에서 데이터를 불러오는데 실패했습니다.")
                    }
                }

                override fun onFailure(call: Call<List<IngredientDto>>, t: Throwable) {
                    Log.e("FoodManagement", "서버 연결 실패", t)
                    showToast("서버 연결에 실패했습니다.")
                }
            })
    }

    // 공유 모드용 데이터 가져오기
    private fun fetchSharedIngredientsFromServer() {
        Log.d("FoodManagement", "공유 모드 식자재 목록 가져오기 시작")

        // 현재 사용자가 속한 그룹의 대표자 ID를 찾기
        findGroupOwnerAndFetchSharedIngredients()
    }

    private fun findGroupOwnerAndFetchSharedIngredients() {
        // 현재 사용자가 속한 그룹 정보를 먼저 조회
        RetrofitClient.userApiService.getMyGroups(currentUserId)
            .enqueue(object : Callback<MyGroupsResponse> {
                override fun onResponse(call: Call<MyGroupsResponse>, response: Response<MyGroupsResponse>) {
                    // 버튼 다시 활성화
                    toggleModeButton.isEnabled = true

                    if (response.isSuccessful) {
                        val myGroups = response.body()

                        when {
                            // 1. 현재 사용자가 대표인 경우 (asOwner에 데이터가 있음)
                            myGroups?.asOwner?.isNotEmpty() == true -> {
                                currentGroupOwnerId = currentUserId
                                // 대표자도 본인 이름을 명시적으로 표시
                                currentGroupOwnerName = UserSession.getUsername(this@FoodManagementActivity)
                                Log.d("FoodManagement", "현재 사용자가 대표자입니다: $currentUserId, 이름: $currentGroupOwnerName")
                                updateModeUI() // API 응답 후 UI 업데이트
                                fetchSharedIngredientsFromServerWithOwnerId(currentUserId)
                            }

                            // 2. 현재 사용자가 구성원인 경우 (asMember에 데이터가 있음)
                            myGroups?.asMember?.isNotEmpty() == true -> {
                                val ownerInfo = myGroups.asMember[0] // 첫 번째 그룹의 대표자
                                currentGroupOwnerId = ownerInfo.groupOwnerId
                                currentGroupOwnerName = ownerInfo.groupOwnerName
                                Log.d("FoodManagement", "구성원으로 속한 그룹의 대표자: ${ownerInfo.groupOwnerId}, 이름: $currentGroupOwnerName")
                                updateModeUI() // API 응답 후 UI 업데이트
                                fetchSharedIngredientsFromServerWithOwnerId(ownerInfo.groupOwnerId)
                            }

                            // 3. 어떤 그룹에도 속하지 않은 경우
                            else -> {
                                Log.d("FoodManagement", "속한 그룹이 없어서 개인 모드로 전환")
                                isSharedMode = false
                                currentGroupOwnerName = ""
                                updateModeUI()
                                showToast("속한 그룹이 없습니다. 개인 모드로 전환됩니다.")
                                fetchIngredientsFromServer()
                            }
                        }
                    } else {
                        Log.e("FoodManagement", "그룹 정보 조회 실패: ${response.code()}")
                        fallbackToPersonalMode()
                    }
                }

                override fun onFailure(call: Call<MyGroupsResponse>, t: Throwable) {
                    // 버튼 다시 활성화
                    toggleModeButton.isEnabled = true
                    Log.e("FoodManagement", "그룹 정보 조회 연결 실패", t)
                    fallbackToPersonalMode()
                }
            })
    }

    private fun fallbackToPersonalMode() {
        showToast("그룹 정보를 불러올 수 없어 개인 모드로 전환됩니다.")
        isSharedMode = false
        currentGroupOwnerName = ""
        toggleModeButton.isEnabled = true  // 버튼 다시 활성화
        updateModeUI()
        fetchIngredientsFromServer()
    }

    private fun fetchSharedIngredientsFromServerWithOwnerId(ownerId: Int) {
        RetrofitClient.ingredientApiService.getSharedIngredients(ownerId)
            .enqueue(object : Callback<List<IngredientDto>> {
                override fun onResponse(call: Call<List<IngredientDto>>, response: Response<List<IngredientDto>>) {
                    if (response.isSuccessful) {
                        val sharedIngredients = response.body()
                        Log.d("FoodManagement", "공유 그룹의 ${sharedIngredients?.size ?: 0}개 식자재 데이터 수신")

                        if (sharedIngredients != null) {
                            processSharedIngredients(sharedIngredients)
                        }
                    } else {
                        Log.e("FoodManagement", "공유 식자재 조회 실패: ${response.code()}")
                        showToast("공유 식자재 조회에 실패했습니다.")
                        // 실패 시 개인 모드로 전환
                        isSharedMode = false
                        updateModeUI()
                        fetchIngredientsFromServer()
                    }
                }

                override fun onFailure(call: Call<List<IngredientDto>>, t: Throwable) {
                    Log.e("FoodManagement", "공유 식자재 조회 연결 실패", t)
                    showToast("서버 연결에 실패했습니다.")
                    // 실패 시 개인 모드로 전환
                    isSharedMode = false
                    updateModeUI()
                    fetchIngredientsFromServer()
                }
            })
    }

    private fun processServerIngredients(serverIngredients: List<IngredientDto>) {
        ingredientsList.clear()
        ingredientIdMap.clear()
        container.removeAllViews()

        // 서버에서 받은 데이터가 현재 사용자의 것인지 한번 더 확인 (보안)
        val userIngredients = serverIngredients.filter { it.userId == currentUserId }

        for (dto in userIngredients) {
            try {
                val expiryDate = apiDateFormat.parse(dto.expiryDate) ?: Date()
                val purchaseDate = apiDateFormat.parse(dto.purchaseDate) ?: Date()

                val ingredient = Ingredient(
                    dto.name,
                    dto.location,
                    expiryDate,
                    purchaseDate,
                    null,
                    dto.imageUrl,
                    dto.userId,  // 소유자 ID
                    dto.ownerName  // 소유자 이름
                )

                ingredientIdMap[ingredient.getUniqueKey()] = dto.id
                ingredientsList.add(ingredient)

            } catch (e: Exception) {
                Log.e("FoodManagement", "데이터 파싱 오류", e)
            }
        }

        handleExpiredIngredients()
    }

    private fun processSharedIngredients(sharedIngredients: List<IngredientDto>) {
        ingredientsList.clear()
        ingredientIdMap.clear()
        container.removeAllViews()

        for (dto in sharedIngredients) {
            try {
                val expiryDate = apiDateFormat.parse(dto.expiryDate) ?: Date()
                val purchaseDate = apiDateFormat.parse(dto.purchaseDate) ?: Date()

                val ingredient = Ingredient(
                    dto.name,
                    dto.location,
                    expiryDate,
                    purchaseDate,
                    null,
                    dto.imageUrl,
                    dto.userId,  // 소유자 ID 설정
                    dto.ownerName  // 소유자 이름 설정
                )

                ingredientIdMap[ingredient.getUniqueKey()] = dto.id
                ingredientsList.add(ingredient)

            } catch (e: Exception) {
                Log.e("FoodManagement", "공유 데이터 파싱 오류", e)
            }
        }

        handleExpiredIngredients()
        showToast("공유 모드로 전환되었습니다 (${ingredientsList.size}개 표시)")
    }

    private fun handleExpiredIngredients() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // 전체 리스트를 소비기한 기준으로 정렬 (오름차순)
        ingredientsList.sortBy { it.expiryDate }

        // 오늘 날짜 확인
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(today)
        val lastAlertDate = sharedPreferences.getString(LAST_ALERT_DATE, "")

        // 소비기한이 지난 식재료 필터링 (개인 모드에서만 또는 본인 것만)
        val expiredIngredients = ingredientsList.filter {
            val calendar = Calendar.getInstance().apply {
                time = it.expiryDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val isExpired = calendar.time.before(today)
            // 개인 모드이거나 공유 모드에서 본인 것만 자동 삭제 확인
            isExpired && (!isSharedMode || it.ownerId == currentUserId)
        }

        // 마지막으로 알림을 표시한 날짜가 오늘이 아닐 경우에만 알림 표시
        if (lastAlertDate != todayStr && expiredIngredients.isNotEmpty()) {
            showExpiredIngredientsAlert(expiredIngredients, todayStr)
        } else {
            // 모든 식자재 카드 표시
            displayAllIngredients()
        }
    }

    private fun showExpiredIngredientsAlert(expiredIngredients: List<Ingredient>, todayStr: String) {
        // 유통기한 지난 항목 처리 (삭제/취소 분기)
        for (expired in expiredIngredients) {
            android.app.AlertDialog.Builder(this@FoodManagementActivity)
                .setTitle("식자재 자동 삭제")
                .setMessage("${expired.name}의 소비기한이 지났습니다. 정말 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    val fakeCard = CardView(this@FoodManagementActivity)
                    deleteIngredient(expired, fakeCard)
                }
                .setNegativeButton("취소") { _, _ ->
                    // 취소한 항목도 표시 (전체 리스트에 추가)
                    if (!ingredientsList.contains(expired)) {
                        ingredientsList.add(expired)
                    }

                    // 소비기한 기준으로 전체 재정렬 (오름차순)
                    ingredientsList.sortBy { it.expiryDate }

                    // 컨테이너 초기화 후 모든 항목 다시 표시
                    displayAllIngredients()
                }
                .show()
        }

        // 알림을 표시한 날짜를 오늘로 저장
        sharedPreferences.edit().putString(LAST_ALERT_DATE, todayStr).apply()
    }

    private fun displayAllIngredients() {
        container.removeAllViews()
        ingredientsList.forEach { ingredient ->
            addIngredientCard(ingredient)
        }
        val modeText = if (isSharedMode) "공유 모드" else "개인 모드"
        showToast("$modeText 데이터 로드 완료 (${ingredientsList.size}개 표시됨)")
    }

    private fun filterIngredients(query: String) {
        container.removeAllViews()

        val filteredList = if (query.isEmpty()) {
            ingredientsList
        } else {
            ingredientsList.filter { it.name.contains(query, ignoreCase = true) }
        }

        // 소비기한 임박순으로 정렬 (오름차순)
        filteredList.sortedBy { it.expiryDate }.forEach { ingredient ->
            addIngredientCard(ingredient)
        }
    }

    private fun showAddIngredientDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.add_ingredient)
        currentDialog = dialog

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.white)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
        }

        setupDialogViews(dialog)
        dialog.show()
    }

    private fun setupDialogViews(dialog: Dialog) {
        val buttonCancel = dialog.findViewById<Button>(R.id.buttonCancel)
        val buttonSave = dialog.findViewById<Button>(R.id.buttonSave)
        val editTextName = dialog.findViewById<EditText>(R.id.editTextName)
        val editTextLocation = dialog.findViewById<EditText>(R.id.editTextLocation)
        val editTextExpiry = dialog.findViewById<EditText>(R.id.editTextExpiry)
        val editTextPurchase = dialog.findViewById<EditText>(R.id.editTextNotes)

        // 이미지 관련 요소 초기화
        imageViewPhoto = dialog.findViewById(R.id.imageViewPhoto)
        val buttonCamera = dialog.findViewById<Button>(R.id.buttonCamera)
        val buttonGallery = dialog.findViewById<Button>(R.id.buttonGallery)

        setupImageButtons(buttonCamera, buttonGallery)
        setupDatePickers(editTextExpiry, editTextPurchase)
        setupDialogButtons(dialog, buttonCancel, buttonSave, editTextName, editTextLocation, editTextExpiry, editTextPurchase)
    }

    private fun setupImageButtons(buttonCamera: Button, buttonGallery: Button) {
        // 카메라 버튼 클릭 시
        buttonCamera.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }

        // 갤러리 버튼 클릭 시
        buttonGallery.setOnClickListener {
            if (checkStoragePermission()) {
                openGallery()
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun setupDatePickers(editTextExpiry: EditText, editTextPurchase: EditText) {
        // DatePicker 처리 추가 - 유통기한
        editTextExpiry.setOnClickListener {
            showDatePickerDialog(editTextExpiry)
        }

        // DatePicker 처리 추가 - 구매날짜
        editTextPurchase.setOnClickListener {
            showDatePickerDialog(editTextPurchase)
        }
    }

    private fun setupDialogButtons(
        dialog: Dialog,
        buttonCancel: Button,
        buttonSave: Button,
        editTextName: EditText,
        editTextLocation: EditText,
        editTextExpiry: EditText,
        editTextPurchase: EditText
    ) {
        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        buttonSave.setOnClickListener {
            try {
                val name = editTextName.text.toString()
                val location = editTextLocation.text.toString()
                val expiryDateStr = editTextExpiry.text.toString()
                val purchaseDateStr = editTextPurchase.text.toString()

                // 기본 유효성 검사
                if (name.isBlank() || location.isBlank() || expiryDateStr.isBlank() || purchaseDateStr.isBlank()) {
                    Toast.makeText(this, "모든 필드를 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 날짜 파싱
                val expiryDate = dateFormat.parse(expiryDateStr) ?: Date()
                val purchaseDate = dateFormat.parse(purchaseDateStr) ?: Date()

                // 데이터 추가 (이미지 경로 포함)
                val newIngredient = Ingredient(
                    name,
                    location,
                    expiryDate,
                    purchaseDate,
                    currentPhotoPath.ifEmpty { null },
                    null,
                    currentUserId,  // 현재 사용자를 소유자로 설정
                    UserSession.getUsername(this)  // 현재 사용자 이름
                )

                // 서버에 데이터 전송
                sendIngredientToServer(newIngredient, dialog)

            } catch (e: Exception) {
                Toast.makeText(this, "데이터 형식을 확인해주세요", Toast.LENGTH_SHORT).show()
                Log.e("FoodManagement", "데이터 추가 오류", e)
            }
        }
    }

    // 카메라 및 갤러리 관련 메서드들
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    private fun checkStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                STORAGE_PERMISSION_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // 파일 생성
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e("FoodManagement", "이미지 파일 생성 실패", ex)
                    null
                }

                // 파일이 생성됐으면 계속 진행
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.AzaAza.foodcare.fileprovider",
                        it
                    )
                    currentPhotoUri = photoURI
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openCamera()
                } else {
                    showToast("카메라 권한이 필요합니다")
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openGallery()
                } else {
                    showToast("저장소 접근 권한이 필요합니다")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    // 카메라로 찍은 사진 처리
                    try {
                        selectedImageBitmap = BitmapFactory.decodeFile(currentPhotoPath)
                        imageViewPhoto.setImageBitmap(selectedImageBitmap)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        showToast("이미지 로드 실패")
                    }
                }
                GALLERY_REQUEST_CODE -> {
                    // 갤러리에서 선택한 사진 처리
                    try {
                        data?.data?.let { uri ->
                            val inputStream = contentResolver.openInputStream(uri)
                            selectedImageBitmap = BitmapFactory.decodeStream(inputStream)
                            imageViewPhoto.setImageBitmap(selectedImageBitmap)

                            // 갤러리에서 선택한 이미지 파일로 저장
                            val photoFile = createImageFile()
                            photoFile.outputStream().use { outputStream ->
                                selectedImageBitmap?.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        showToast("이미지 로드 실패")
                    }
                }
            }
        }
    }

    private fun sendIngredientToServer(ingredient: Ingredient, dialog: Dialog) {
        val namePart = ingredient.name.toRequestBody("text/plain".toMediaTypeOrNull())
        val locationPart = ingredient.location.toRequestBody("text/plain".toMediaTypeOrNull())
        val expiryDatePart = apiDateFormat.format(ingredient.expiryDate).toRequestBody("text/plain".toMediaTypeOrNull())
        val purchaseDatePart = apiDateFormat.format(ingredient.purchaseDate).toRequestBody("text/plain".toMediaTypeOrNull())
        val userIdPart = currentUserId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        var imagePart: MultipartBody.Part? = null

        if (ingredient.imagePath != null) {
            val imageFile = File(ingredient.imagePath)
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
        }

        RetrofitClient.ingredientApiService.addIngredient(
            namePart, locationPart, expiryDatePart, purchaseDatePart, userIdPart, imagePart
        ).enqueue(object : Callback<IngredientResponse> {
            override fun onResponse(call: Call<IngredientResponse>, response: Response<IngredientResponse>) {
                if (response.isSuccessful) {
                    // 서버에서 식별자 할당
                    val newId = ingredientsList.size + 1
                    ingredientIdMap[ingredient.getUniqueKey()] = newId

                    ingredientsList.add(ingredient)

                    // 소비기한 임박순으로 정렬 (오름차순)
                    ingredientsList.sortBy { it.expiryDate }

                    container.removeAllViews()
                    ingredientsList.forEach { addIngredientCard(it) }

                    showToast(response.body()?.message ?: "${ingredient.name} 추가 성공!")
                    dialog.dismiss()

                    // 추가 후 현재 모드에 맞게 데이터 새로고침
                    if (isSharedMode) {
                        fetchSharedIngredientsFromServer()
                    } else {
                        fetchIngredientsFromServer()
                    }
                } else {
                    showToast("서버 오류: ${response.code()}")
                    Log.e("FoodManagement", "서버 응답 오류: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<IngredientResponse>, t: Throwable) {
                showToast("서버 연결에 실패했습니다.")
                Log.e("FoodManagement", "서버 연결 실패", t)
            }
        })
    }

    // 식자재 삭제 기능
    private fun deleteIngredient(ingredient: Ingredient, cardView: CardView) {
        val ingredientId = ingredientIdMap[ingredient.getUniqueKey()]
        if (ingredientId == null) {
            showToast("식자재 ID를 찾을 수 없습니다.")
            return
        }

        // 삭제 요청을 보낼 때 로딩 표시
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("삭제 중...")
            setCancelable(false)
            show()
        }

        // 서버에 삭제 요청 (현재 사용자 ID와 함께)
        Log.d("FoodManagement", "식자재 삭제 요청 - ID: $ingredientId, 사용자: $currentUserId, 이름: ${ingredient.name}")

        RetrofitClient.ingredientApiService.deleteIngredient(ingredientId, currentUserId)
            .enqueue(object : Callback<IngredientResponse> {
                override fun onResponse(call: Call<IngredientResponse>, response: Response<IngredientResponse>) {
                    progressDialog.dismiss()

                    if (response.isSuccessful) {
                        ingredientsList.remove(ingredient)
                        ingredientIdMap.remove(ingredient.getUniqueKey())
                        container.removeView(cardView)
                        showToast("${ingredient.name}이(가) 삭제되었습니다.")
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                        when (response.code()) {
                            403 -> showToast("본인의 식자재만 삭제할 수 있습니다.")
                            404 -> showToast("해당 식자재를 찾을 수 없습니다.")
                            else -> showToast("삭제 실패: ${response.code()}")
                        }
                        Log.e("FoodManagement", "삭제 실패 - 코드: ${response.code()}, 응답: $errorBody")
                    }
                }

                override fun onFailure(call: Call<IngredientResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    Log.e("FoodManagement", "서버 연결 실패", t)
                    showToast("서버 연결에 실패했습니다: ${t.message}")
                }
            })
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(selectedYear, selectedMonth, selectedDay)
                val formattedDate = dateFormat.format(selectedDate.time)
                editText.setText(formattedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }

    private fun addIngredientCard(ingredient: Ingredient) {
        val inflater = LayoutInflater.from(this)
        val cardView = inflater.inflate(R.layout.ingredient_card, container, false) as CardView

        // 카드 내용 설정
        val nameTextView = cardView.findViewById<TextView>(R.id.textViewName)
        val expiryTextView = cardView.findViewById<TextView>(R.id.textViewExpiry)
        val locationTextView = cardView.findViewById<TextView>(R.id.textViewLocation)
        val imageView = cardView.findViewById<ImageView>(R.id.imageView)

        // 공유 모드에서는 식자재명(사용자명) 형식으로 표시
        if (isSharedMode && ingredient.ownerName != null) {
            nameTextView.text = "${ingredient.name}(${ingredient.ownerName})"
        } else {
            nameTextView.text = ingredient.name
        }

        // 이미지 설정
        if (ingredient.imagePath != null) {
            val bitmap = BitmapFactory.decodeFile(ingredient.imagePath)
            imageView.setImageBitmap(bitmap)
        } else if (ingredient.imageUrl != null) {
            Glide.with(this)
                .load("https://foodcare-69ae76eec1bf.herokuapp.com${ingredient.imageUrl}")
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.basicfood)
        }

        setupExpiryDisplay(ingredient, expiryTextView)

        val purchaseStr = displayDateFormat.format(ingredient.purchaseDate)

        // 공유 모드에서는 사용자명 제거 (이미 식자재명에 포함됨)
        locationTextView.text = "${ingredient.location} · 구입 $purchaseStr"

        setupCardClickListeners(cardView, ingredient)

        container.addView(cardView)
    }

    private fun setupExpiryDisplay(ingredient: Ingredient, expiryTextView: TextView) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val diffDays = ((ingredient.expiryDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
        val expiryStr = displayDateFormat.format(ingredient.expiryDate)

        when {
            diffDays < 0 -> {
                // 소비기한이 지난 경우: "소비기한: X일 지남" 형태로 표시
                val daysOverdue = Math.abs(diffDays)
                expiryTextView.text = "소비기한: ${daysOverdue}일 지남"
                expiryTextView.setBackgroundResource(R.drawable.expiry_background) // 빨간색
                expiryTextView.setTextColor(Color.WHITE)
            }
            diffDays == 0 || isSameDay(ingredient.expiryDate, today) -> {
                expiryTextView.text = "소비기한: 오늘까지"
                expiryTextView.setBackgroundResource(R.drawable.expiry_background) // 빨간색
                expiryTextView.setTextColor(Color.WHITE)
            }
            diffDays in 1..3 -> {
                expiryTextView.text = "소비기한: ${diffDays}일 남음"
                expiryTextView.setBackgroundResource(R.drawable.expiry_warning_background) // 주황색
                expiryTextView.setTextColor(Color.WHITE)
            }
            diffDays >= 4 -> {
                // 4일 이상 남은 경우
                if (diffDays <= 7) {
                    expiryTextView.text = "소비기한: ${diffDays}일 남음"
                    expiryTextView.setBackgroundResource(R.drawable.expiry_safe_background) // 초록색
                    expiryTextView.setTextColor(Color.WHITE)
                } else {
                    // 8일 이상 남은 경우는 날짜 표시
                    expiryTextView.text = "소비기한: $expiryStr"
                    expiryTextView.setBackgroundResource(R.drawable.expiry_safe_background) // 초록색
                    expiryTextView.setTextColor(Color.WHITE)
                }
            }
        }

        expiryTextView.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
    }

    private fun setupCardClickListeners(cardView: CardView, ingredient: Ingredient) {
        // 카드 클릭 이벤트 - 레시피 검색
        cardView.setOnClickListener {
            val intent = Intent(this, RecipeSearchActivity::class.java)
            intent.putExtra("SELECTED_INGREDIENT", ingredient.name)
            startActivity(intent)
        }

        cardView.setOnLongClickListener {
            // 공유 모드에서는 본인 것만 삭제 가능
            if (isSharedMode && ingredient.ownerId != currentUserId) {
                showToast("본인이 등록한 식자재만 삭제할 수 있습니다.")
                return@setOnLongClickListener true
            }

            android.app.AlertDialog.Builder(this)
                .setTitle("삭제 확인")
                .setMessage("${ingredient.name}을(를) 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    deleteIngredient(ingredient, cardView)
                }
                .setNegativeButton("취소", null)
                .show()
            true
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}