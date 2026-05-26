package com.AzaAza.foodcare.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.adapter.CategoryAdapter
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.CategoryDto
import com.AzaAza.foodcare.models.ExpenseDto
import com.AzaAza.foodcare.models.SharedExpenseDto
import com.AzaAza.foodcare.models.MyGroupsResponse
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import com.AzaAza.foodcare.adapter.ExpenseGroupAdapter
import com.AzaAza.foodcare.models.ExpenseGroup
import com.AzaAza.foodcare.data.UserSession
import com.AzaAza.foodcare.models.CategorySummary

class ExpenseActivity : AppCompatActivity() {

    companion object {
        // 고정된 카테고리 색상 지정
        val CATEGORY_COLORS = mapOf(
            "외식" to Color.parseColor("#5B8FF9"),   // 푸른색
            "배달" to Color.parseColor("#FF6B3B"),   // 주황색
            "주류" to Color.parseColor("#FFD666"),   // 노란색
            "장보기" to Color.parseColor("#65D1AA"), // 연두색
            "간식" to Color.parseColor("#F28CB1"),   // 분홍색
            "기타" to Color.parseColor("#8A67E8")    // 보라색
        )

        private const val TAG = "ExpenseActivity"
    }

    private lateinit var pieChart: PieChart
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var totalExpenseText: TextView
    private lateinit var comparisonTextView: TextView
    private lateinit var categoryAdapter: CategoryAdapter

    private lateinit var currentMonthText: TextView
    private lateinit var prevMonthButton: ImageButton
    private lateinit var nextMonthButton: ImageButton

    // 탭 관련 UI 요소
    private lateinit var personalModeTab: LinearLayout
    private lateinit var sharedModeTab: LinearLayout
    private lateinit var personalModeIcon: ImageView
    private lateinit var sharedModeIcon: ImageView
    private lateinit var personalModeText: TextView
    private lateinit var sharedModeText: TextView
    private lateinit var sharedModeInfo: LinearLayout
    private lateinit var sharedModeInfoText: TextView

    private val categories = ArrayList<CategoryDto>()

    private var currentMonthTotal: Double = 0.0
    private var previousMonthTotal: Double = 0.0

    // 현재 선택된 연도와 월
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0

    // 현재 실제 연도와 월 (현재 날짜)
    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    // 현재 모드 (개인/공유)
    private var isSharedMode: Boolean = false

    // 공유 모드에서 사용할 그룹 오너 ID
    private var groupOwnerId: Int = -1

    // 현재 대화상자의 어댑터를 추적하기 위한 변수 추가
    private var currentGroupAdapter: ExpenseGroupAdapter? = null

    lateinit var adapter: ExpenseGroupAdapter

    // 사용자 ID -> 이름 매핑을 위한 맵
    private val userIdToNameMap = mutableMapOf<Int, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        // 로그인 안 되어있으면 바로 종료
        if (!checkUserLogin()) {
            return
        }

        initializeViews()
        setupDateNavigation()
        setupEventListeners()
        setupRecyclerView()

        // 모드 설정 초기화 (Intent 확인 포함)
        initializeModeSettings()

        // 초기 데이터 로드
        loadInitialData()
    }

    private fun initializeModeSettings() {
        // intent에 공유 모드 정보 담겨있으면 그걸로 설정
        val intentSharedMode = intent.getBooleanExtra("shared_mode", false)
        val intentOwnerId = intent.getIntExtra("group_owner_id", -1)

        if (intentSharedMode && intentOwnerId != -1) {
            // Intent로 명시적으로 공유 모드가 지정된 경우
            isSharedMode = true
            groupOwnerId = intentOwnerId
            Log.d(TAG, "Intent로 공유 모드 설정 - groupOwnerId: $groupOwnerId")

            // 공유 모드일 때 구성원 정보 로드
            loadGroupMembers()
        } else {
            // 기본적으로 개인 모드로 시작
            isSharedMode = false
            groupOwnerId = UserSession.getUserId(this)
            Log.d(TAG, "기본 개인 모드로 설정")
        }

        updateModeUI()
    }

    private fun checkUserLogin(): Boolean {
        val userId = UserSession.getUserId(this)
        if (userId == -1) {
            Log.e(TAG, "사용자가 로그인되지 않음")
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }

        Log.d(TAG, "사용자 로그인 확인됨 - userId: $userId")
        return true
    }

    //공유 모드에서 사용자 정보 로드
    private fun loadGroupMembers() {
        if (!isSharedMode || groupOwnerId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== 그룹 구성원 정보 로드 시작 ===")
                val members = RetrofitClient.expenseApiService.getMembers(groupOwnerId)

                Log.d(TAG, "그룹 구성원 수: ${members.size}")
                members.forEach { member ->
                    Log.d(TAG, "구성원: ID=${member.id}, Name=${member.username}, LoginId=${member.loginId}")
                }

                withContext(Dispatchers.Main) {
                    // 사용자 ID → 이름 매핑 저장 (공유 모드에서 사용됨)
                    userIdToNameMap.clear()
                    members.forEach { member ->
                        userIdToNameMap[member.id] = member.username
                        Log.d(TAG, "매핑 저장: ${member.id} -> ${member.username}")
                    }

                    Log.d(TAG, "최종 사용자 매핑: $userIdToNameMap")
                }

            } catch (exception: Exception) {
                Log.e(TAG, "그룹 구성원 정보 로드 실패", exception)
            }
        }
    }

    //사용자 ID로 이름 조회
    private fun getUserNameById(userId: Int): String {
        val currentUserId = UserSession.getUserId(this)
        return when {
            userId == currentUserId -> "나"
            userIdToNameMap.containsKey(userId) -> userIdToNameMap[userId]!!
            else -> {
                Log.w(TAG, "사용자 ID $userId 에 대한 이름을 찾을 수 없음")
                "구성원"
            }
        }
    }


    //사용자가 속한 그룹의 소유자 ID 조회
    private fun findGroupOwnerId(callback: (Int?) -> Unit) {
        val userId = UserSession.getUserId(this)
        if (userId == -1) {
            callback(null)
            return
        }

        RetrofitClient.userApiService.getMyGroups(userId).enqueue(object : retrofit2.Callback<MyGroupsResponse> {
            override fun onResponse(call: retrofit2.Call<MyGroupsResponse>, response: retrofit2.Response<MyGroupsResponse>) {
                if (response.isSuccessful) {
                    val myGroups = response.body()
                    if (myGroups != null) {
                        Log.d(TAG, "사용자 그룹 정보: $myGroups")

                        // 1. 사용자가 대표자인 경우 (자신의 ID 사용)
                        if (myGroups.asOwner.isNotEmpty()) {
                            callback(userId)
                            Log.d(TAG, "사용자가 대표자임: ownerId = $userId")
                        }
                        // 2. 사용자가 구성원인 경우 (대표자의 ID 사용)
                        else if (myGroups.asMember.isNotEmpty()) {
                            val ownerId = myGroups.asMember[0].groupOwnerId
                            callback(ownerId)
                            Log.d(TAG, "사용자가 구성원임: ownerId = $ownerId")
                        }
                        // 3. 어떤 그룹에도 속하지 않은 경우
                        else {
                            callback(userId) // 자신을 대표자로 설정 (개인 모드)
                            Log.d(TAG, "그룹에 속하지 않음: 개인 모드로 설정")
                        }
                    } else {
                        Log.e(TAG, "그룹 정보 응답이 null")
                        callback(userId) // 실패 시 자신을 대표자로 설정
                    }
                } else {
                    Log.e(TAG, "그룹 정보 요청 실패: ${response.code()}")
                    callback(userId) // 실패 시 자신을 대표자로 설정
                }
            }

            override fun onFailure(call: retrofit2.Call<MyGroupsResponse>, t: Throwable) {
                Log.e(TAG, "그룹 소유자 ID 조회 실패", t)
                callback(userId) // 실패 시 자신을 대표자로 설정
            }
        })
    }

    //개인 모드로 전환
    private fun switchToPersonalMode() {
        if (!isSharedMode) return

        isSharedMode = false
        groupOwnerId = UserSession.getUserId(this) // 자신의 ID로 재설정
        userIdToNameMap.clear() // 매핑 초기화
        updateModeUI()
        loadInitialData() // 데이터 새로고침 (카테고리 + 월별 데이터)
        Log.d(TAG, "개인 모드로 전환 완료")
    }


    //공유 모드로 전환
    private fun switchToSharedMode() {
        if (isSharedMode) return

        Log.d(TAG, "공유 모드 전환 시작")

        // 그룹 소유자 ID 조회
        findGroupOwnerId { ownerId ->
            if (ownerId != null) {
                groupOwnerId = ownerId
                isSharedMode = true
                updateModeUI()

                // 공유 모드 전환 시 구성원 정보 로드
                loadGroupMembers()

                loadSharedModeInfo() // 공유 모드 정보 로드

                // 디버깅 추가
                Handler(Looper.getMainLooper()).postDelayed({
                }, 2000) // 2초 후 디버깅 실행

                loadInitialData() // 데이터 새로고침 (카테고리 + 월별 데이터)
                Log.d(TAG, "공유 모드로 전환 완료 - groupOwnerId: $groupOwnerId")
            } else {
                Toast.makeText(this, "공유 그룹 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "공유 모드 전환 실패: groupOwnerId가 null")
            }
        }
    }

    private fun loadSharedModeInfo() {
        if (!isSharedMode || groupOwnerId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val members = RetrofitClient.expenseApiService.getMembers(groupOwnerId)
                val userId = UserSession.getUserId(this@ExpenseActivity)
                val currentUser = members.find { it.id == userId }

                // 대표자 이름 추출
                val ownerName = members.find { it.isOwner }?.username ?: "알 수 없음"
                val totalMemberCount = members.size

                withContext(Dispatchers.Main) {
                    val infoText = if (currentUser?.isOwner == true) {
                        // ✅ 대표자인 경우: 구성원 이름만 표시
                        val memberNames = members.filter { !it.isOwner }
                            .map { it.username }
                            .joinToString(", ")

                        "가족 ${totalMemberCount}명과 공유 중 · $ownerName (대표)" +
                                if (memberNames.isNotEmpty()) ", $memberNames" else ""
                    } else {
                        // ✅ 구성원인 경우: 본인 포함한 전체 구성원 이름 표시
                        val memberNames = members.filter { !it.isOwner }
                            .map { it.username }
                            .joinToString(", ")

                        "가족 ${totalMemberCount}명과 공유 중 · $ownerName (대표)" +
                                if (memberNames.isNotEmpty()) ", $memberNames" else ""
                    }

                    sharedModeInfoText.text = infoText
                    Log.d(TAG, "공유 모드 정보 로드 완료: $infoText")
                }

            } catch (exception: Exception) {
                Log.e(TAG, "공유 모드 정보 로드 실패", exception)
                withContext(Dispatchers.Main) {
                    sharedModeInfoText.text = "공유 그룹 정보 로드 실패"
                }
            }
        }
    }


    private fun loadCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = UserSession.getUserId(this@ExpenseActivity)
                if (userId == -1) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExpenseActivity, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d(TAG, "카테고리 데이터 요청 시작 - userId: $userId, isSharedMode: $isSharedMode")

                val categoryResponse = if (isSharedMode) {
                    // 공유 모드: 공유 그룹의 카테고리 조회
                    RetrofitClient.expenseApiService.getSharedCategories(groupOwnerId)
                } else {
                    // 개인 모드: 개인 카테고리 조회
                    RetrofitClient.expenseApiService.getCategories(userId)
                }

                Log.d(TAG, "카테고리 데이터 응답: ${categoryResponse.size}개")

                withContext(Dispatchers.Main) {
                    categories.clear()
                    categories.addAll(categoryResponse)

                    Log.d(TAG, "카테고리 리스트 업데이트됨:")
                    categories.forEach { category ->
                        Log.d(TAG, "- ${category.name} (ID: ${category.id})")
                    }

                    categoryAdapter.notifyDataSetChanged()
                    Log.d(TAG, "카테고리 어댑터 업데이트 완료")
                }

            } catch (exception: Exception) {
                Log.e(TAG, "카테고리 데이터 로드 실패", exception)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ExpenseActivity,
                        "카테고리 데이터 로드 실패: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadMonthlyData() {
        val previousCalendar = Calendar.getInstance()
        previousCalendar.set(selectedYear, selectedMonth - 1, 1)
        previousCalendar.add(Calendar.MONTH, -1)
        val previousYear = previousCalendar.get(Calendar.YEAR)
        val previousMonth = previousCalendar.get(Calendar.MONTH) + 1

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = UserSession.getUserId(this@ExpenseActivity)
                if (userId == -1) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExpenseActivity, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d(TAG, "=== 월별 데이터 로드 시작 ===")
                Log.d(TAG, "요청 파라미터: userId=$userId, year=$selectedYear, month=$selectedMonth, shared=$isSharedMode, groupOwnerId=$groupOwnerId")

                val categorySummaries: List<CategorySummary>

                if (isSharedMode) {
                    Log.d(TAG, "공유 모드 월별 요약 요청")
                    val currentMonthData = RetrofitClient.expenseApiService
                        .getSharedMonthlySummary(groupOwnerId, selectedYear, selectedMonth)

                    Log.d(TAG, "공유 모드 응답: totalAmount=${currentMonthData.totalAmount}, categories=${currentMonthData.categories.size}개")
                    currentMonthData.categories.forEach { category ->
                        Log.d(TAG, "  카테고리: ${category.categoryName} = ${category.amount}원")
                    }

                    currentMonthTotal = currentMonthData.totalAmount
                    categorySummaries = currentMonthData.categories

                    previousMonthTotal = try {
                        val prev = RetrofitClient.expenseApiService
                            .getSharedMonthlySummary(groupOwnerId, previousYear, previousMonth)
                        prev.totalAmount
                    } catch (e: Exception) {
                        Log.w(TAG, "이전 달 데이터 요청 실패 (공유)", e)
                        0.0
                    }

                } else {
                    Log.d(TAG, "개인 모드 월별 요약 요청")
                    val currentMonthData = RetrofitClient.expenseApiService
                        .getMonthlySummary(userId, selectedYear, selectedMonth)

                    currentMonthTotal = currentMonthData.totalAmount
                    categorySummaries = currentMonthData.categories

                    previousMonthTotal = try {
                        val prev = RetrofitClient.expenseApiService
                            .getMonthlySummary(userId, previousYear, previousMonth)
                        prev.totalAmount
                    } catch (e: Exception) {
                        Log.w(TAG, "이전 달 데이터 요청 실패 (개인)", e)
                        0.0
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        val formatter = NumberFormat.getInstance(Locale.KOREA)
                        totalExpenseText.text = "${formatter.format(currentMonthTotal.toInt())}원"

                        updateComparisonText()
                        updatePieChart(categorySummaries)
                        updateCategoryList(categorySummaries)

                        Log.d(TAG, "월별 데이터 UI 업데이트 완료")
                    } catch (exception: Exception) {
                        Log.e(TAG, "UI 업데이트 실패", exception)
                        Toast.makeText(this@ExpenseActivity, "화면 업데이트 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                loadExpensesForMonth()

            } catch (exception: Exception) {
                Log.e(TAG, "월별 데이터 로드 전체 실패", exception)
                withContext(Dispatchers.Main) {
                    handleDataLoadError(exception)
                }
            }
        }
    }

    private fun loadExpensesForMonth() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = UserSession.getUserId(this@ExpenseActivity)
                if (userId == -1) return@launch

                val selectedMonthStr = String.format("%d-%02d", selectedYear, selectedMonth)
                Log.d(TAG, "=== 월별 지출 내역 로드 ===")
                Log.d(TAG, "필터링 기준: $selectedMonthStr")

                if (isSharedMode) {
                    Log.d(TAG, "공유 모드 지출 내역 조회: groupOwnerId=$groupOwnerId")
                    val sharedExpenseResponse = RetrofitClient.expenseApiService.getSharedExpenses(groupOwnerId)

                    Log.d(TAG, "전체 공유 지출 개수: ${sharedExpenseResponse.size}")
                    sharedExpenseResponse.forEachIndexed { index, expense ->
                        Log.d(TAG, "[$index] ${expense.productName}, 날짜: ${expense.dateTime}, 작성자: ${expense.ownerName}, 사용자ID: ${expense.userId}")
                    }

                    val filteredSharedExpenses = sharedExpenseResponse.filter {
                        it.dateTime.startsWith(selectedMonthStr)
                    }

                    Log.d(TAG, "선택한 달 공유 지출 내역 로드 완료: ${filteredSharedExpenses.size}개")
                } else {
                    val expenseResponse = RetrofitClient.expenseApiService.getExpenses(userId)
                    val filteredExpenses = expenseResponse.filter {
                        it.dateTime.startsWith(selectedMonthStr)
                    }

                    Log.d(TAG, "선택한 달 지출 내역 로드 완료: ${filteredExpenses.size}개")
                }

            } catch (exception: Exception) {
                Log.e(TAG, "지출 내역 로드 실패", exception)
            }
        }
    }

    private fun showAddExpenseDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense_new, null)

        val productNameEdit = dialogView.findViewById<EditText>(R.id.productNameEdit)
        val amountEdit = dialogView.findViewById<EditText>(R.id.amountEdit)
        val dateButton = dialogView.findViewById<Button>(R.id.dateButton)
        val memoEdit = dialogView.findViewById<EditText>(R.id.memoEdit)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.categorySpinner)

        // 현재 선택된 달로 날짜 설정
        val calendar = Calendar.getInstance()
        if (selectedYear == currentYear && selectedMonth == currentMonth) {
            // 현재 날짜 사용
        } else {
            // 선택된 달의 1일로 설정
            calendar.set(Calendar.YEAR, selectedYear)
            calendar.set(Calendar.MONTH, selectedMonth - 1)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
        }

        // 시간 부분 기본값 설정 (00:00)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateButton.text = dateFormat.format(calendar.time)

        // 날짜 선택 (기존 로직과 동일)
        dateButton.setOnClickListener {
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, year, month, day ->
                // 선택된 날짜가 현재 달의 범위 내에 있는지 확인
                if (year == selectedYear && month + 1 == selectedMonth) {
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    dateButton.text = dateFormat.format(calendar.time)
                } else {
                    Toast.makeText(this, "선택한 달의 날짜만 입력할 수 있습니다", Toast.LENGTH_SHORT).show()
                }
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), currentDay).apply {
                // DatePicker의 범위를 선택된 월의 범위로 제한
                datePicker.minDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth - 1, 1)
                }.timeInMillis

                datePicker.maxDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth - 1, getActualMaximum(Calendar.DAY_OF_MONTH))
                    // 현재 달이면 현재 날짜까지만 선택 가능하도록 제한
                    if (selectedYear == currentYear && selectedMonth == currentMonth) {
                        set(currentYear, currentMonth - 1, get(Calendar.DAY_OF_MONTH))
                    }
                }.timeInMillis
            }.show()
        }

        // 카테고리 스피너 설정
        val categoryNames = categories.map { it.name }
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryNames)
        categorySpinner.adapter = categoryAdapter

        val dialogTitle = if (isSharedMode) "지출 입력 (공유 모드)" else "지출 입력"

        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val productName = productNameEdit.text.toString().trim()
                val amountText = amountEdit.text.toString().trim()
                val memo = memoEdit.text.toString().trim()
                val selectedCategoryPos = categorySpinner.selectedItemPosition

                // 입력 검증 (기존 로직과 동일)
                if (productName.isEmpty()) {
                    Toast.makeText(this, "상품명을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (amountText.isEmpty()) {
                    Toast.makeText(this, "금액을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (selectedCategoryPos == -1 || selectedCategoryPos >= categories.size) {
                    Toast.makeText(this, "카테고리를 선택해주세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val amount = amountText.replace(",", "").toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "올바른 금액을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)
                val selectedCategory = categories[selectedCategoryPos]

                // 서버에 데이터 저장 (공유/개인 모드 구분 없이 동일한 API 사용)
                saveExpense(selectedCategory.id, productName, amount, dateTime, memo)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showCategoryDetailDialog(category: CategoryDto) {
        Log.d(TAG, "카테고리 상세 다이얼로그 열기: ${category.name} (ID: ${category.id})")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_detail, null)

        val categoryNameText = dialogView.findViewById<TextView>(R.id.categoryNameText)
        val categoryNameTextSmall = dialogView.findViewById<TextView>(R.id.categoryNameTextSmall)
        val totalAmountText = dialogView.findViewById<TextView>(R.id.totalAmountText)
        val expenseGroupRecyclerView = dialogView.findViewById<RecyclerView>(R.id.expenseGroupRecyclerView)
        val emptyStateLayout = dialogView.findViewById<LinearLayout>(R.id.emptyStateLayout)
        val closeButton = dialogView.findViewById<ImageView>(R.id.closeButton)

        categoryNameText.text = category.name
        categoryNameTextSmall.text = category.name

        val formatter = NumberFormat.getInstance(Locale.KOREA)
        totalAmountText.text = "${formatter.format(category.totalAmount.toInt())}원"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        // 카테고리별 지출 내역 로드
        loadCategoryExpenses(category.id, category) { expenseList, sharedExpenseList ->
            val hasExpenses = if (isSharedMode) {
                sharedExpenseList.isNotEmpty()
            } else {
                expenseList.isNotEmpty()
            }

            if (hasExpenses) {
                emptyStateLayout.visibility = View.GONE
                expenseGroupRecyclerView.visibility = View.VISIBLE

                val expenseGroups = if (isSharedMode) {
                    // ✅ 공유 모드일 때 현재 사용자 ID와 사용자 매핑을 적용해야 내 지출이 보임!
                    groupSharedExpensesByDate(sharedExpenseList).apply {
                        forEach { group ->
                            group.updateCurrentUserId(UserSession.getUserId(this@ExpenseActivity))
                            group.setUserMapping(userIdToNameMap)
                        }
                    }
                } else {
                    groupExpensesByDate(expenseList)
                }

                adapter = ExpenseGroupAdapter(expenseGroups.toMutableList()) { expense ->
                    val userId = UserSession.getUserId(this)
                    if (expense.userId == userId) {
                        showDeleteExpenseDialog(expense.id, adapter)
                    } else {
                        Toast.makeText(this, "본인이 작성한 지출만 삭제할 수 있습니다", Toast.LENGTH_SHORT).show()
                    }
                }

                adapter.setOnTotalChangedListener { newTotal ->
                    val newFormattedTotal = "${formatter.format(newTotal.toFloat().toInt())}원"
                    totalAmountText.text = newFormattedTotal
                }

                expenseGroupRecyclerView.layoutManager = LinearLayoutManager(this)
                expenseGroupRecyclerView.adapter = adapter
                currentGroupAdapter = adapter

                for (group in expenseGroups) {
                    group.isExpanded = true
                }
                adapter.notifyDataSetChanged()

            } else {
                expenseGroupRecyclerView.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun loadCategoryExpenses(
        categoryId: Int,
        category: CategoryDto,
        callback: (List<ExpenseDto>, List<SharedExpenseDto>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = UserSession.getUserId(this@ExpenseActivity)
                if (userId == -1) return@launch

                Log.d(TAG, "=== 카테고리별 지출 내역 로드 시작 ===")
                Log.d(TAG, "categoryId: $categoryId, category.name: ${category.name}")
                Log.d(TAG, "isSharedMode: $isSharedMode, groupOwnerId: $groupOwnerId, userId: $userId")
                Log.d(TAG, "현재 사용자 매핑: $userIdToNameMap")

                if (isSharedMode) {
                    Log.d(TAG, "공유 모드 - 전체 공유 지출 조회 시작")
                    val allSharedExpenses = RetrofitClient.expenseApiService.getSharedExpenses(groupOwnerId)

                    Log.d(TAG, "=== 전체 공유 지출 분석 ===")
                    Log.d(TAG, "전체 공유 지출 개수: ${allSharedExpenses.size}")

                    allSharedExpenses.forEachIndexed { index, expense ->
                        Log.d(TAG, "[$index] ${expense.productName}")
                        Log.d(TAG, "      ID: ${expense.id}, CategoryID: ${expense.categoryId}")
                        Log.d(TAG, "      Date: ${expense.dateTime}")
                        Log.d(TAG, "      UserId: ${expense.userId}")
                        Log.d(TAG, "      서버 OwnerName: '${expense.ownerName}'")
                        Log.d(TAG, "      매핑된 이름: '${getUserNameById(expense.userId)}'")
                        Log.d(TAG, "      현재사용자 여부: ${expense.userId == userId}")
                        Log.d(TAG, "")
                    }

                    // 현재 사용자가 작성한 지출 확인
                    val myExpenses = allSharedExpenses.filter { it.userId == userId }
                    Log.d(TAG, "=== 현재 사용자가 작성한 지출 ===")
                    Log.d(TAG, "개수: ${myExpenses.size}")
                    myExpenses.forEach { expense ->
                        Log.d(TAG, "  - ${expense.productName} (${expense.dateTime})")
                    }

                    // 카테고리 ID로 필터링
                    val categoryFilteredExpenses = allSharedExpenses.filter { expense ->
                        expense.categoryName?.trim()?.equals(category.name.trim(), ignoreCase = true) == true
                    }

                    Log.d(TAG, "=== 카테고리 필터링 결과 ===")
                    Log.d(TAG, "카테고리 $categoryId 지출 개수: ${categoryFilteredExpenses.size}")
                    categoryFilteredExpenses.forEach { expense ->
                        Log.d(TAG, "  - ${expense.productName}: userId=${expense.userId}, 매핑명='${getUserNameById(expense.userId)}'")
                    }

                    // 선택된 월로 필터링
                    val selectedMonthStr = String.format("%d-%02d", selectedYear, selectedMonth)
                    Log.d(TAG, "날짜 필터링 기준: $selectedMonthStr")

                    val filteredSharedExpenses = categoryFilteredExpenses.filter { expense ->
                        expense.dateTime.startsWith(selectedMonthStr)
                    }

                    Log.d(TAG, "=== 최종 필터링 결과 ===")
                    Log.d(TAG, "최종 지출 개수: ${filteredSharedExpenses.size}")
                    filteredSharedExpenses.forEach { expense ->
                        Log.d(TAG, "  최종: ${expense.productName}")
                        Log.d(TAG, "        작성자 ID: ${expense.userId}")
                        Log.d(TAG, "        서버 ownerName: '${expense.ownerName}'")
                        Log.d(TAG, "        매핑된 이름: '${getUserNameById(expense.userId)}'")
                        Log.d(TAG, "        현재사용자 작성: ${expense.userId == userId}")
                    }

                    withContext(Dispatchers.Main) {
                        callback(emptyList(), filteredSharedExpenses)
                    }
                } else {
                    // 개인 모드: 기존 로직 유지
                    Log.d(TAG, "개인 모드 - 카테고리별 지출 조회")
                    val expenses = RetrofitClient.expenseApiService.getExpensesByCategory(categoryId, userId)

                    Log.d(TAG, "개인 모드 - 카테고리 ${categoryId}의 전체 지출: ${expenses.size}개")

                    // 선택된 월 필터링
                    val selectedMonthStr = String.format("%d-%02d", selectedYear, selectedMonth)
                    val filteredExpenses = expenses.filter {
                        it.dateTime.startsWith(selectedMonthStr)
                    }

                    Log.d(TAG, "필터링된 개인 지출: ${filteredExpenses.size}개")

                    withContext(Dispatchers.Main) {
                        callback(filteredExpenses, emptyList())
                    }
                }

            } catch (exception: Exception) {
                Log.e(TAG, "카테고리별 지출 내역 로드 실패", exception)
                withContext(Dispatchers.Main) {
                    callback(emptyList(), emptyList())
                }
            }
        }
    }

    private fun groupExpensesByDate(expenses: List<ExpenseDto>): List<ExpenseGroup> {
        val groups = mutableMapOf<String, ExpenseGroup>()
        val currentUserId = UserSession.getUserId(this)

        for (expense in expenses) {
            val date = expense.dateTime.split(" ")[0] // "yyyy-MM-dd" 부분만 추출

            if (!groups.containsKey(date)) {
                val group = ExpenseGroup(
                    date = date,
                    displayTitle = formatDateTitle(date),
                    isSharedMode = false
                )
                // 현재 사용자 ID 설정
                group.updateCurrentUserId(currentUserId)
                groups[date] = group
            }

            groups[date]?.expenses?.add(expense)
        }

        return groups.values.sortedByDescending { it.date }
    }


    private fun groupSharedExpensesByDate(sharedExpenses: List<SharedExpenseDto>): List<ExpenseGroup> {
        val groups = mutableMapOf<String, ExpenseGroup>()
        val currentUserId = UserSession.getUserId(this)  // 현재 로그인된 사용자 ID

        for (expense in sharedExpenses) {
            val date = expense.dateTime.split(" ")[0] // "yyyy-MM-dd"만 추출

            if (!groups.containsKey(date)) {
                val group = ExpenseGroup(
                    date = date,
                    displayTitle = formatDateTitle(date),
                    isSharedMode = true
                )

                // ✅ 중요: 사용자 정보 연결
                group.updateCurrentUserId(currentUserId)
                group.setUserMapping(userIdToNameMap)

                groups[date] = group
            }

            groups[date]?.sharedExpenses?.add(expense)
        }

        val sortedGroups = groups.values.sortedByDescending { it.date }

        Log.d(TAG, "공유 모드 그룹 수: ${sortedGroups.size}")
        sortedGroups.forEach { group ->
            Log.d(TAG, " - 날짜: ${group.date}, 항목 수: ${group.sharedExpenses.size}")
        }

        return sortedGroups
    }

    private fun formatDateTitle(date: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterday.time)

        return when (date) {
            today -> "오늘"
            yesterdayStr -> "어제"
            else -> date
        }
    }

    fun showDeleteExpenseDialog(expenseId: Int, adapter: ExpenseGroupAdapter) {
        AlertDialog.Builder(this)
            .setTitle("지출 삭제")
            .setMessage("이 지출 내역을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteExpense(expenseId, adapter)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteExpense(expenseId: Int, adapter: ExpenseGroupAdapter) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = UserSession.getUserId(this@ExpenseActivity)
                if (userId == -1) return@launch

                val response = RetrofitClient.expenseApiService.deleteExpense(expenseId, userId)

                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        // 어댑터에서 아이템 제거
                        val removed = adapter.removeExpense(expenseId)
                        if (removed) {
                            Toast.makeText(this@ExpenseActivity, "지출이 삭제되었습니다", Toast.LENGTH_SHORT).show()
                            // 전체 데이터 새로고침
                            loadMonthlyData()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExpenseActivity, "삭제 실패: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (exception: Exception) {
                Log.e(TAG, "지출 삭제 실패", exception)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "삭제 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveExpense(
        categoryId: Int,
        productName: String,
        amount: Double,
        dateTime: String,
        memo: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = UserSession.getUserId(this@ExpenseActivity)
                if (userId == -1) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExpenseActivity, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // ExpenseDto 생성
                val expense = ExpenseDto(
                    id = 0,
                    categoryId = categoryId,
                    productName = productName.trim(),
                    amount = amount,
                    dateTime = dateTime,
                    memo = memo.trim().ifEmpty { null },
                    categoryName = null,
                    createdAt = null,
                    userId = userId
                )

                Log.d(TAG, "지출 저장 요청: userId=$userId, 공유모드=$isSharedMode, groupOwnerId=$groupOwnerId")
                Log.d(TAG, "저장할 지출: $expense")

                // 서버 요청 전송
                val response = RetrofitClient.expenseApiService.addExpense(expense)

                if (response.isSuccessful) {
                    Log.d(TAG, "지출 저장 성공: ${response.body()}")
                    withContext(Dispatchers.Main) {
                        val message = if (isSharedMode) {
                            "공유 그룹에 지출이 추가되었습니다"
                        } else {
                            "지출이 추가되었습니다"
                        }
                        Toast.makeText(this@ExpenseActivity, message, Toast.LENGTH_SHORT).show()

                        // 저장 후 즉시 디버깅 실행
                        if (isSharedMode) {
                            Handler(Looper.getMainLooper()).postDelayed({
                            }, 1000) // 1초 후 디버깅
                        }

                        // 데이터 새로고침 - 카테고리와 월별 데이터 모두 새로고침
                        loadInitialData()
                    }
                } else {
                    Log.e(TAG, "지출 저장 실패: ${response.code()} - ${response.message()}")
                    withContext(Dispatchers.Main) {
                        val errorMessage = when (response.code()) {
                            400 -> "입력 데이터가 올바르지 않습니다."
                            403 -> "권한이 없습니다."
                            404 -> "사용자 또는 카테고리를 찾을 수 없습니다."
                            500 -> "서버 내부 오류가 발생했습니다."
                            else -> "저장 실패: ${response.message()}"
                        }
                        Toast.makeText(this@ExpenseActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

            } catch (exception: Exception) {
                Log.e(TAG, "지출 저장 중 예외 발생", exception)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "저장 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateComparisonText() {
        val difference = currentMonthTotal - previousMonthTotal
        val formatter = NumberFormat.getInstance(Locale.KOREA)

        if (difference > 0) {
            comparisonTextView.text = "+ ${formatter.format(difference.toInt())}"
            comparisonTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        } else if (difference < 0) {
            comparisonTextView.text = "- ${formatter.format(abs(difference.toInt()))}"
            comparisonTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            comparisonTextView.text = "변동 없음"
            comparisonTextView.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        }
    }

    private fun updateCategoryList(categorySummaries: List<CategorySummary>) {
        Log.d(TAG, "=== updateCategoryList 시작 ===")
        Log.d(TAG, "categories 리스트 크기: ${categories.size}")
        Log.d(TAG, "categorySummaries 크기: ${categorySummaries.size}")

        // 현재 categories 리스트 상세 로깅
        Log.d(TAG, "현재 categories 리스트:")
        categories.forEachIndexed { index, category ->
            Log.d(TAG, "[$index] ID: ${category.id}, Name: '${category.name}', TotalAmount: ${category.totalAmount}")
        }

        // categorySummaries 상세 로깅
        Log.d(TAG, "categorySummaries 리스트:")
        categorySummaries.forEachIndexed { index, summary ->
            Log.d(TAG, "[$index] CategoryName: '${summary.categoryName}', Amount: ${summary.amount}")
        }

        // 모든 카테고리의 totalAmount를 0으로 초기화
        for (category in categories) {
            category.totalAmount = 0.0
            Log.d(TAG, "카테고리 '${category.name}' 초기화됨")
        }

        // 요약 데이터와 매칭하여 totalAmount 업데이트
        var matchedCount = 0
        for (summary in categorySummaries) {
            Log.d(TAG, "매칭 시도: 요약 카테고리 '${summary.categoryName}' (${summary.amount}원)")

            val matchingCategory = categories.find { category ->
                val isMatch = category.name.trim().equals(summary.categoryName.trim(), ignoreCase = true)
                Log.d(TAG, "  - '${category.name}' vs '${summary.categoryName}' = $isMatch")
                isMatch
            }

            if (matchingCategory != null) {
                matchingCategory.totalAmount = summary.amount
                matchedCount++
                Log.d(TAG, "✅ 매칭 성공: '${matchingCategory.name}' = ${matchingCategory.totalAmount}원")
            } else {
                Log.w(TAG, "❌ 매칭 실패: '${summary.categoryName}' - 해당 카테고리를 찾을 수 없음")

                // 중복 체크: 이미 같은 이름의 카테고리가 있는지 확인
                val isDuplicate = categories.any { category ->
                    category.name.trim().equals(summary.categoryName.trim(), ignoreCase = true)
                }

                if (!isDuplicate) {
                    // 서버에서 새로운 카테고리가 생성된 경우에만 추가
                    val newCategory = CategoryDto(
                        id = 0, // 임시 ID
                        name = summary.categoryName,
                        userId = UserSession.getUserId(this),
                        totalAmount = summary.amount
                    )
                    categories.add(newCategory)
                    matchedCount++
                    Log.d(TAG, "🆕 새 카테고리 추가: '${newCategory.name}' = ${newCategory.totalAmount}원")
                } else {
                    Log.w(TAG, "⚠️ 중복 카테고리 발견, 추가하지 않음: '${summary.categoryName}'")
                }
            }
        }

        Log.d(TAG, "매칭 완료: $matchedCount/${categorySummaries.size}개 매칭됨")

        // 중복 카테고리 제거 (추가 안전장치)
        val uniqueCategories = categories.distinctBy { it.name.trim().lowercase() }.toMutableList()
        if (uniqueCategories.size != categories.size) {
            Log.w(TAG, "중복 카테고리 제거: ${categories.size}개 -> ${uniqueCategories.size}개")
            categories.clear()
            categories.addAll(uniqueCategories)
        }

        // 어댑터에 색상 업데이트
        val categoryAdapterColors = categories.map { category ->
            CATEGORY_COLORS[category.name] ?: Color.GRAY
        }
        categoryAdapter.updateColors(categoryAdapterColors)

        // 어댑터 데이터 변경 알림
        categoryAdapter.notifyDataSetChanged()

        Log.d(TAG, "=== 최종 카테고리 리스트 ===")
        categories.forEachIndexed { index, category ->
            Log.d(TAG, "[$index] '${category.name}' = ${category.totalAmount}원")
        }
        Log.d(TAG, "=== updateCategoryList 완료 ===")
    }

    private fun updatePieChart(categorySummaries: List<CategorySummary>) {
        if (categorySummaries.sumOf { it.amount } <= 0.0) {
            pieChart.visibility = View.GONE
            return
        } else {
            pieChart.visibility = View.VISIBLE
        }

        val pieEntries = ArrayList<PieEntry>()
        val categoryColors = ArrayList<Int>()

        // 파이차트에 쓸 데이터 설정
        for (category in categorySummaries) {
            if (category.amount > 0) {
                pieEntries.add(PieEntry(category.amount.toFloat(), category.categoryName))
                val color = CATEGORY_COLORS[category.categoryName] ?: Color.GRAY
                categoryColors.add(color)
            }
        }

        val dataSet = PieDataSet(pieEntries, "")
        dataSet.colors = categoryColors

        val pieData = PieData(dataSet)
        pieData.setValueTextSize(12f)
        pieData.setDrawValues(false)

        pieChart.setDrawEntryLabels(false)
        pieChart.data = pieData
        pieChart.description.isEnabled = false
        pieChart.centerText = "지출 분포"
        pieChart.setCenterTextSize(16f)
        pieChart.legend.isEnabled = false
        pieChart.animateY(1000)
        pieChart.invalidate()
    }

    private fun handleDataLoadError(error: Exception) {
        val errorMessage = "데이터 로드 실패: ${error.message ?: "알 수 없는 오류"}"
        Log.e(TAG, errorMessage, error)
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

        totalExpenseText.text = "0원"
        comparisonTextView.text = "데이터 없음"
        pieChart.visibility = View.GONE
        categories.forEach { it.totalAmount = 0.0 }
        categoryAdapter.notifyDataSetChanged()
    }

    /**
     * 모드 UI 업데이트
     */
    private fun updateModeUI() {
        if (isSharedMode) {
            // 개인 모드 비활성화
            personalModeTab.setBackgroundColor(Color.TRANSPARENT)
            personalModeIcon.setColorFilter(Color.parseColor("#999999"))
            personalModeText.setTextColor(Color.parseColor("#999999"))

            // 공유 모드 활성화
            sharedModeTab.setBackgroundResource(R.drawable.bg_toggle_selected_green_rect)
            sharedModeIcon.setColorFilter(Color.parseColor("#00C896"))
            sharedModeText.setTextColor(Color.parseColor("#00C896"))

            sharedModeInfo.visibility = View.VISIBLE
        } else {
            // 공유 모드 비활성화
            sharedModeTab.setBackgroundColor(Color.TRANSPARENT)
            sharedModeIcon.setColorFilter(Color.parseColor("#999999"))
            sharedModeText.setTextColor(Color.parseColor("#999999"))

            // 개인 모드 활성화
            personalModeTab.setBackgroundResource(R.drawable.bg_toggle_selected_blue_rect)
            personalModeIcon.setColorFilter(Color.parseColor("#007AFF"))
            personalModeText.setTextColor(Color.parseColor("#007AFF"))

            sharedModeInfo.visibility = View.GONE
        }
    }

    private fun initializeViews() {
        // 뒤로가기 버튼
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        // 메인 UI 요소
        pieChart = findViewById(R.id.expensePieChart)
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView)
        totalExpenseText = findViewById(R.id.totalExpenseText)
        comparisonTextView = findViewById(R.id.comparisonTextView)

        // 탭 UI 초기화
        personalModeTab = findViewById(R.id.personalModeTab)
        sharedModeTab = findViewById(R.id.sharedModeTab)
        personalModeIcon = findViewById(R.id.personalModeIcon)
        sharedModeIcon = findViewById(R.id.sharedModeIcon)
        personalModeText = findViewById(R.id.personalModeText)
        sharedModeText = findViewById(R.id.sharedModeText)
        sharedModeInfo = findViewById(R.id.sharedModeInfo)
        sharedModeInfoText = findViewById(R.id.sharedModeInfoText)

        // 공유 모드 탭 활성화 (중요!)
        sharedModeTab.isEnabled = true
        sharedModeTab.alpha = 1.0f
        sharedModeTab.isClickable = true

        // 개인 모드 탭도 활성화
        personalModeTab.isEnabled = true
        personalModeTab.alpha = 1.0f
        personalModeTab.isClickable = true

        Log.d(TAG, "UI 초기화 완료 - 모든 탭 활성화됨")
    }

    private fun setupDateNavigation() {
        // 현재 날짜로 초기화
        val calendar = Calendar.getInstance()
        currentYear = calendar.get(Calendar.YEAR)
        currentMonth = calendar.get(Calendar.MONTH) + 1 // 월은 0부터 시작하므로 +1

        // 선택된 연도와 월을 현재 연도와 월로 초기화
        selectedYear = currentYear
        selectedMonth = currentMonth

        // 연월 선택 UI 초기화
        currentMonthText = findViewById(R.id.currentMonthText)
        prevMonthButton = findViewById(R.id.prevMonthButton)
        nextMonthButton = findViewById(R.id.nextMonthButton)

        updateMonthYearText()
        updateNextButtonState()
    }

    private fun setupEventListeners() {
        // 탭 이벤트 리스너
        personalModeTab.setOnClickListener {
            switchToPersonalMode()
        }

        sharedModeTab.setOnClickListener {
            switchToSharedMode()
        }

        // 날짜 네비게이션 이벤트
        currentMonthText.setOnClickListener {
            showMonthYearPicker()
        }

        prevMonthButton.setOnClickListener {
            moveToPreviousMonth()
        }

        nextMonthButton.setOnClickListener {
            moveToNextMonth()
        }

        // 지출 추가 버튼
        val fabAddExpense: View = findViewById(R.id.fabAddExpense)
        fabAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }
    }

    private fun setupRecyclerView() {
        categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        categoryAdapter = CategoryAdapter(categories) { category ->
            // 카테고리 클릭 시 세부 내역 화면으로 이동
            showCategoryDetailDialog(category)
        }
        categoriesRecyclerView.adapter = categoryAdapter
    }


    private fun verifyLoadedCategories() {
        Log.d(TAG, "=== 로드된 카테고리 검증 ===")
        Log.d(TAG, "카테고리 개수: ${categories.size}")

        if (categories.isEmpty()) {
            Log.e(TAG, "❌ 카테고리가 비어있음!")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userId = UserSession.getUserId(this@ExpenseActivity)
                    val testCategories = if (isSharedMode) {
                        RetrofitClient.expenseApiService.getSharedCategories(groupOwnerId)
                    } else {
                        RetrofitClient.expenseApiService.getCategories(userId)
                    }

                    Log.d(TAG, "재확인 요청 결과: ${testCategories.size}개")
                    testCategories.forEach { category ->
                        Log.d(TAG, "  - ID: ${category.id}, Name: '${category.name}', UserID: ${category.userId}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "재확인 요청 실패: ${e.message}")
                }
            }
        } else {
            categories.forEach { category ->
                Log.d(TAG, "✅ ID: ${category.id}, Name: '${category.name}', Amount: ${category.totalAmount}")
            }
        }
        Log.d(TAG, "=== 검증 완료 ===")
    }

    private fun loadInitialData() {
        Log.d(TAG, "=== 초기 데이터 로드 시작 ===")
        Log.d(TAG, "isSharedMode: $isSharedMode, groupOwnerId: $groupOwnerId")

        // 먼저 카테고리를 로드하고, 완료 후 월별 데이터 로드
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 카테고리 먼저 로드
                val userId = UserSession.getUserId(this@ExpenseActivity)
                if (userId == -1) {
                    Log.e(TAG, "사용자 ID가 유효하지 않음")
                    return@launch
                }

                Log.d(TAG, "카테고리 요청 파라미터: userId=$userId, isSharedMode=$isSharedMode")

                val categoryResponse = if (isSharedMode) {
                    Log.d(TAG, "공유 카테고리 요청: groupOwnerId=$groupOwnerId")
                    RetrofitClient.expenseApiService.getSharedCategories(groupOwnerId)
                } else {
                    Log.d(TAG, "개인 카테고리 요청: userId=$userId")
                    RetrofitClient.expenseApiService.getCategories(userId)
                }

                Log.d(TAG, "서버 응답: ${categoryResponse.size}개 카테고리")
                categoryResponse.forEach { category ->
                    Log.d(TAG, "  서버 카테고리: ID=${category.id}, Name='${category.name}', UserID=${category.userId}")
                }

                withContext(Dispatchers.Main) {
                    // 기존 카테고리 목록 완전히 초기화
                    categories.clear()

                    // 중복 제거된 카테고리만 추가
                    val uniqueCategories = categoryResponse.distinctBy { it.name.trim().lowercase() }
                    categories.addAll(uniqueCategories)

                    // 카테고리 순서 정렬 (외식, 배달, 장보기, 간식, 주류, 기타)
                    val categoryOrder = listOf("외식", "배달", "장보기", "간식", "주류", "기타")

                    categories.sortWith { category1, category2 ->
                        val index1 = categoryOrder.indexOf(category1.name)
                        val index2 = categoryOrder.indexOf(category2.name)

                        when {
                            index1 == -1 && index2 == -1 -> category1.name.compareTo(category2.name) // 둘 다 정의되지 않은 경우 이름순
                            index1 == -1 -> 1
                            index2 == -1 -> -1
                            else -> index1.compareTo(index2)
                        }
                    }

                    Log.d(TAG, "중복 제거 후 카테고리: ${categoryResponse.size}개 -> ${categories.size}개")
                    Log.d(TAG, "정렬된 카테고리 순서: ${categories.map { it.name }}")

                    categoryAdapter.notifyDataSetChanged()

                    Log.d(TAG, "UI 업데이트 완료: ${categories.size}개 카테고리")

                    // 로드된 카테고리 검증
                    verifyLoadedCategories()

                    // 2. 카테고리 로드 완료 후 월별 데이터 로드
                    loadMonthlyData()
                }

            } catch (exception: Exception) {
                Log.e(TAG, "초기 데이터 로드 실패", exception)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "카테고리 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    loadMonthlyData()
                }
            }
        }
    }

    private fun updateMonthYearText() {
        currentMonthText.text = "${selectedYear}년 ${selectedMonth}월"
    }

    private fun updateNextButtonState() {
        // 현재 월 선택돼있으면 다음 달 버튼 비활성화
        if (selectedYear == currentYear && selectedMonth == currentMonth) {
            nextMonthButton.isEnabled = false
            nextMonthButton.alpha = 0.5f
        } else {
            nextMonthButton.isEnabled = true
            nextMonthButton.alpha = 1.0f
        }
    }

    private fun moveToPreviousMonth() {
        if (selectedMonth == 1) {
            selectedYear--
            selectedMonth = 12
        } else {
            selectedMonth--
        }
        updateMonthYearText()
        updateNextButtonState()
        loadMonthlyData()
    }

    private fun moveToNextMonth() {
        // 현재 월보다 더 미래로 갈 수 없도록 체크
        if (selectedYear == currentYear && selectedMonth == currentMonth) {
            return
        }

        if (selectedMonth == 12) {
            selectedYear++
            selectedMonth = 1
        } else {
            selectedMonth++
        }
        updateMonthYearText()
        updateNextButtonState()
        loadMonthlyData()
    }

    private fun showMonthYearPicker() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_month_year_picker, null)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)

        // 월 설정 (1~12)
        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = selectedMonth

        // 연도 설정 (앱 시작 연도부터 현재 연도까지)
        val appStartYear = 2024
        yearPicker.minValue = appStartYear
        yearPicker.maxValue = currentYear
        yearPicker.value = selectedYear

        AlertDialog.Builder(this)
            .setTitle("연월 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val newYear = yearPicker.value
                val newMonth = monthPicker.value

                // 미래 날짜 선택 방지
                if (newYear > currentYear || (newYear == currentYear && newMonth > currentMonth)) {
                    Toast.makeText(this, "미래 날짜는 선택할 수 없습니다", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                selectedYear = newYear
                selectedMonth = newMonth
                updateMonthYearText()
                updateNextButtonState()
                loadMonthlyData()
            }
            .setNegativeButton("취소", null)
            .show()
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