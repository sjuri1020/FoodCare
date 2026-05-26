package com.AzaAza.foodcare.ui

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.api.UserInfoApiService
import com.AzaAza.foodcare.models.Allergen
import com.AzaAza.foodcare.models.Disease
import com.AzaAza.foodcare.models.HealthInfoRequest
import com.AzaAza.foodcare.models.HealthInfoResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.widget.*
import com.google.android.flexbox.FlexboxLayout
import androidx.appcompat.widget.AppCompatSpinner
import com.AzaAza.foodcare.data.UserSession


class UserInfoActivity : AppCompatActivity() {
    private lateinit var api: UserInfoApiService

    private var healthInfoId: Int? = null

    private var allergenList: List<Allergen> = emptyList()
    private var diseaseList: List<Disease> = emptyList()
    private val selectedAllergens = mutableListOf<Allergen>()
    private val selectedDiseases = mutableListOf<Disease>()
    private var allergenRestored = false
    private var diseaseRestored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info)

        val currentUserId = UserSession.getUserId(this)
        if (currentUserId == -1) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val spinnerAllergen = findViewById<AppCompatSpinner>(R.id.spinnerAllergen)
        val spinnerDisease = findViewById<AppCompatSpinner>(R.id.spinnerDisease)


        findViewById<TextView>(R.id.topBarTitle).text = "개인 정보 입력"
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        api = RetrofitClient.userInfoApiService

        val etYear = findViewById<EditText>(R.id.etYear)
        val spinnerMonth = findViewById<Spinner>(R.id.spinnerMonth)
        val spinnerDay = findViewById<Spinner>(R.id.spinnerDay)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        val spinnerFood = findViewById<Spinner>(R.id.spinnerFood)

        val btnAddAllergen = findViewById<Button>(R.id.btnAddAllergen)
        val flexAllergens = findViewById<FlexboxLayout>(R.id.flexAllergens)

        val btnAddDisease = findViewById<Button>(R.id.btnAddDisease)
        val flexDiseases = findViewById<FlexboxLayout>(R.id.flexDiseases)

        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val scrollView = findViewById<ScrollView>(R.id.scrollView)


        // Spinner 세팅
        val months = (1..12).map { it.toString().padStart(2, '0') }
        spinnerMonth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        val days = (1..31).map { it.toString().padStart(2, '0') }
        spinnerDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, days)

        val genderOptions = listOf("남성", "여성", "기타")
        spinnerGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genderOptions)

        val foodOptions = listOf("한식", "일식", "중식", "아시아요리", "양식", "디저트")
        spinnerFood.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, foodOptions)




        // 알레르기 콜백 부분
        api.getAllAllergens().enqueue(object : Callback<List<Allergen>> {
            override fun onResponse(call: Call<List<Allergen>>, response: Response<List<Allergen>>) {
                if (response.isSuccessful) {
                    allergenList = response.body() ?: emptyList()
                    spinnerAllergen.adapter = ArrayAdapter(
                        this@UserInfoActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("알레르기 선택") + allergenList.map { it.name }
                    )

                    // 복원은 콜백 안에서만 실행! (비동기 safe)
                    if (!allergenRestored) {
                        // "수정"인 경우 intent에 기존 id 리스트가 들어옴
                        val ids = intent.getIntegerArrayListExtra("EXTRA_ALLERGEN_IDS") ?: arrayListOf()
                        selectedAllergens.clear()
                        // 받아온 전체 리스트에서 id가 일치하는 것만 추가
                        allergenList.forEach { if (it.id in ids) selectedAllergens.add(it) }
                        allergenRestored = true
                    }
                    updateAllergenFlex(flexAllergens, scrollView)
                }
            }
            override fun onFailure(call: Call<List<Allergen>>, t: Throwable) {}
        })


        // 질병 콜백 부분
        api.getAllDiseases().enqueue(object : Callback<List<Disease>> {
            override fun onResponse(call: Call<List<Disease>>, response: Response<List<Disease>>) {
                if (response.isSuccessful) {
                    diseaseList = response.body() ?: emptyList()
                    spinnerDisease.adapter = ArrayAdapter(
                        this@UserInfoActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("질병 선택") + diseaseList.map { it.name }
                    )


                    if (!diseaseRestored) {
                        val ids = intent.getIntegerArrayListExtra("EXTRA_DISEASE_IDS") ?: arrayListOf()
                        selectedDiseases.clear()
                        diseaseList.forEach { if (it.id in ids) selectedDiseases.add(it) }
                        diseaseRestored = true
                    }
                    updateDiseaseFlex(flexDiseases, scrollView)
                }
            }
            override fun onFailure(call: Call<List<Disease>>, t: Throwable) {}
        })

        // 알레르기 추가 버튼
        btnAddAllergen.setOnClickListener {
            Log.d("알레르기추가", "버튼 클릭됨!")
            if (allergenList.isEmpty()) {
                Toast.makeText(this, "알레르기 목록이 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pos = spinnerAllergen.selectedItemPosition
            Log.d("알레르기추가", "스피너 pos: $pos / 선택한 이름: ${if (pos > 0) allergenList[pos-1].name else "없음"}")
            if (pos > 0) {
                val selected = allergenList[pos - 1]
                Log.d("알레르기추가", "selected.id: ${selected.id}, selected.name: ${selected.name}")
                Log.d("알레르기추가", "현재 리스트: " + selectedAllergens.joinToString { "${it.id}:${it.name}" })
                if (selectedAllergens.any { it.id == selected.id }) {
                    Log.d("알레르기추가", "이미 추가됨! -> ${selected.id}:${selected.name}")
                    Toast.makeText(this, "이미 추가된 알레르기입니다.", Toast.LENGTH_SHORT).show()
                } else {
                    selectedAllergens.add(selected)
                    Log.d("알레르기추가", "추가 후 리스트: " + selectedAllergens.joinToString { "${it.id}:${it.name}" })
                    updateAllergenFlex(flexAllergens, scrollView)
                }
            }
        }



        // 질병 추가 버튼
        btnAddDisease.setOnClickListener {
            if (diseaseList.isEmpty()) {
                Toast.makeText(this, "질병 목록이 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pos = spinnerDisease.selectedItemPosition
            if (pos > 0) {
                val selected = diseaseList[pos - 1]
                if (selectedDiseases.none { it.id == selected.id }) {
                    selectedDiseases.add(selected)
                    updateDiseaseFlex(flexDiseases, scrollView)
                }
                spinnerDisease.setSelection(0)
            }
        }

        fun parseBirthDate(date: String?): Triple<String, String, String> {
            val d = date?.split("-") ?: return Triple("", "01", "01")
            val y = d.getOrNull(0) ?: ""
            val m = d.getOrNull(1) ?: "01"
            val dd = d.getOrNull(2) ?: "01"
            return Triple(y, m, dd)
        }

        // 기존 값 설정 (수정 모드)
        intent?.let {
            healthInfoId = it.getIntExtra("EXTRA_HEALTH_INFO_ID", -1).takeIf { id -> id != -1 }
            val (yy, mm, dd) = parseBirthDate(it.getStringExtra("EXTRA_BIRTH_DATE"))
            etYear.setText(yy)
            spinnerMonth.setSelection(months.indexOf(mm))
            spinnerDay.setSelection(days.indexOf(dd))
            spinnerGender.setSelection(genderOptions.indexOf(it.getStringExtra("EXTRA_GENDER") ?: "남성"))
            etHeight.setText(it.getDoubleExtra("EXTRA_HEIGHT", 0.0).toString())
            etWeight.setText(it.getDoubleExtra("EXTRA_WEIGHT", 0.0).toString())
            spinnerFood.setSelection(foodOptions.indexOf(it.getStringExtra("EXTRA_FOOD_PREF") ?: "한식"))
        }

        btnSubmit.setOnClickListener {
            val year = etYear.text.toString().padStart(4, '0')
            val month = spinnerMonth.selectedItem.toString().padStart(2, '0')
            val day = spinnerDay.selectedItem.toString().padStart(2, '0')
            val birthDate = "$year-$month-$day"

            val genderKor = spinnerGender.selectedItem.toString().trim()
            val genderMapping = mapOf("남성" to "Male", "여성" to "Female", "기타" to "Other")
            val gender = genderMapping[genderKor] ?: "Other"

            val foodPrefKor = spinnerFood.selectedItem.toString().trim()
            val foodPrefMapping = mapOf(
                "한식" to "한식",
                "일식" to "일식",
                "중식" to "중식",
                "아시아요리" to "아시아요리",
                "양식" to "양식",
                "디저트" to "디저트"
            )
            val foodPref = foodPrefMapping[foodPrefKor] ?: "한식"   // 반드시 이거!

            val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val allergenIds = selectedAllergens.map { it.id }
            val diseaseIds = selectedDiseases.map { it.id }

            val request = HealthInfoRequest(
                user_id = currentUserId,
                birth_date = birthDate,
                gender = gender,
                height_cm = height,
                weight_kg = weight,
                food_preference = foodPref, // 꼭 한글!
                allergen_ids = allergenIds,
                disease_ids = diseaseIds
            )
            Log.d("서버저장", request.toString())

            val call = healthInfoId?.let { id -> api.updateHealthInfo(id, request) } ?: api.createHealthInfo(request)
            call.enqueue(object : Callback<HealthInfoResponse> {
                override fun onResponse(call: Call<HealthInfoResponse>, response: Response<HealthInfoResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@UserInfoActivity, "저장 완료", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@UserInfoActivity, "저장 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                        // 서버 로그에도 꼭 실제 에러 내용 남기기!
                    }
                }
                override fun onFailure(call: Call<HealthInfoResponse>, t: Throwable) {
                    Toast.makeText(this@UserInfoActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
        }


    }

    private fun updateAllergenFlex(flexbox: FlexboxLayout, scrollView: ScrollView) {
        flexbox.removeAllViews()
        val allergenCopy = selectedAllergens.toList()
        allergenCopy.forEach { allergen ->
            val tv = TextView(this)
            tv.text = allergen.name
            tv.setPadding(24, 8, 24, 8)
            tv.setBackgroundResource(R.drawable.rounded_chip)
            tv.setOnClickListener {
                selectedAllergens.removeAll { it.id == allergen.id }
                updateAllergenFlex(flexbox, scrollView)
            }
            val params = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(10, 10, 0, 10)
            tv.layoutParams = params
            flexbox.addView(tv)
        }
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun updateDiseaseFlex(flexbox: FlexboxLayout, scrollView: ScrollView) {
        flexbox.removeAllViews()
        val diseaseCopy = selectedDiseases.toList()
        diseaseCopy.forEach { disease ->
            val tv = TextView(this)
            tv.text = disease.name
            tv.setPadding(24, 8, 24, 8)
            tv.setBackgroundResource(R.drawable.rounded_chip)
            tv.setOnClickListener {
                selectedDiseases.removeAll { it.id == disease.id }
                updateDiseaseFlex(flexbox, scrollView)
            }
            val params = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(10, 10, 0, 10)
            tv.layoutParams = params
            flexbox.addView(tv)
        }
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }


}

