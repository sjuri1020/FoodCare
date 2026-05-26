package com.AzaAza.foodcare.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.api.UserInfoApiService
import com.AzaAza.foodcare.models.HealthInfoResponse
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Call
import android.graphics.Color
import android.util.TypedValue
import com.AzaAza.foodcare.data.UserSession
import com.google.android.flexbox.FlexboxLayout

class UserInfoShowActivity : AppCompatActivity() {
    private lateinit var api: UserInfoApiService

    private lateinit var tvBirthDate: TextView
    private lateinit var tvGender: TextView
    private lateinit var tvHeight: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvFoodPref: TextView
    private lateinit var btnEditInfo: Button

    private var currentInfo: HealthInfoResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info_show)

        findViewById<TextView>(R.id.topBarTitle).text = "개인 정보"
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        api = RetrofitClient.userInfoApiService

        tvBirthDate = findViewById(R.id.tvBirthDate)
        tvGender    = findViewById(R.id.tvGender)
        tvHeight    = findViewById(R.id.tvHeight)
        tvWeight    = findViewById(R.id.tvWeight)
        tvFoodPref  = findViewById(R.id.tvFoodPref)
        btnEditInfo = findViewById(R.id.btnEditInfo)


        loadHealthInfo()

        btnEditInfo.setOnClickListener {
            Log.d("UserInfoShow", "추가하러가기 버튼 클릭됨")
            val intent = Intent(this@UserInfoShowActivity, UserInfoActivity::class.java)

            currentInfo?.let { info ->
                intent.putExtra("EXTRA_HEALTH_INFO_ID", info.id)
                intent.putExtra("EXTRA_BIRTH_DATE", info.birth_date)
                intent.putExtra("EXTRA_GENDER", info.gender)
                intent.putExtra("EXTRA_HEIGHT", info.height_cm)
                intent.putExtra("EXTRA_WEIGHT", info.weight_kg)
                intent.putExtra("EXTRA_FOOD_PREF", info.food_preference)
                intent.putExtra("EXTRA_ALLERGEN_IDS", ArrayList(currentInfo?.allergens?.map { it.id } ?: emptyList()))
                intent.putExtra("EXTRA_DISEASE_IDS", ArrayList(currentInfo?.diseases?.map { it.id } ?: emptyList()))
            }

            startActivity(intent)
        }


    }

    private fun loadHealthInfo() {
        val userId = UserSession.getUserId(this)   // 또는 currentUserId, 네가 사용하는 id 변수
        api.listHealthInfo(userId).enqueue(object: Callback<List<HealthInfoResponse>> {
            override fun onResponse(call: Call<List<HealthInfoResponse>>, response: Response<List<HealthInfoResponse>>) {
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    if (list.isNotEmpty()) {
                        displayInfo(list[0])
                        currentInfo = list[0]
                    } else {
                        displayInfo(null)
                    }
                } else {
                    Toast.makeText(this@UserInfoShowActivity, "조회 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<List<HealthInfoResponse>>, t: Throwable) {
                Toast.makeText(this@UserInfoShowActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun displayInfo(info: HealthInfoResponse?) {
        tvBirthDate.text = info?.birth_date ?: ""
        tvGender.text    = when (info?.gender) {
            "Male" -> "남성"
            "Female" -> "여성"
            "Other" -> "기타"
            else -> ""
        }
        tvHeight.text    = info?.height_cm?.toString() ?: ""
        tvWeight.text    = info?.weight_kg?.toString() ?: ""
        tvFoodPref.text  = info?.food_preference ?: ""
        setFlexChips(findViewById(R.id.allergenFlexbox), info?.allergens?.map { it.name } ?: emptyList())
        setFlexChips(findViewById(R.id.diseaseFlexbox), info?.diseases?.map { it.name } ?: emptyList())
    }


    private fun setFlexChips(flexbox: FlexboxLayout, names: List<String>) {
        flexbox.removeAllViews()
        for (name in names) {
            val chip = TextView(this)
            chip.text = name
            chip.setPadding(30, 10, 30, 10)
            chip.setBackgroundResource(R.drawable.rounded_chip)
            chip.setTextColor(Color.BLACK)
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val lp = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(10, 10, 0, 10)
            chip.layoutParams = lp
            flexbox.addView(chip)
        }
    }



    override fun onResume() {
        super.onResume()
        loadHealthInfo()  // 항상 다시 불러오기
    }

}
