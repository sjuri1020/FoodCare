package com.AzaAza.foodcare.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.IngredientDto
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import com.AzaAza.foodcare.data.UserSession

class ExpiryNotificationActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val displayDateFormat = SimpleDateFormat("MM월 dd일", Locale.KOREA)
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class Ingredient(
        val id: Int,
        val name: String,
        val location: String,
        val expiryDate: Date,
        val purchaseDate: Date,
        val imageUrl: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expiry_notification)

        container = findViewById(R.id.expiryNotificationContainer)

        // 뒤로가기 버튼
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        // 서버에서 식자재 데이터 불러오기
        fetchIngredientsFromServer()
    }

    private fun fetchIngredientsFromServer() {
        // 현재 사용자 ID 가져오기
        val currentUserId = UserSession.getUserId(this)
        if (currentUserId == -1) {
            showToast("로그인이 필요합니다.")
            finish()
            return
        }

        Log.d("ExpiryNotification", "현재 사용자 ID: $currentUserId")

        // 현재 사용자의 식자재만 조회
        RetrofitClient.ingredientApiService.getIngredients(currentUserId)
            .enqueue(object : Callback<List<IngredientDto>> {
                override fun onResponse(call: Call<List<IngredientDto>>, response: Response<List<IngredientDto>>) {
                    if (response.isSuccessful) {
                        val ingredients = response.body()
                        Log.d("ExpiryNotification", "서버 응답 성공, 식자재 개수: ${ingredients?.size}")

                        if (ingredients != null) {
                            // 한번 더 사용자 필터링 (보안상)
                            val userIngredients = ingredients.filter { it.userId == currentUserId }
                            Log.d("ExpiryNotification", "사용자 필터링 후 식자재 개수: ${userIngredients.size}")
                            processIngredients(userIngredients)
                        } else {
                            showToast("데이터를 불러오는데 실패했습니다: 빈 응답")
                            showEmptyMessage()
                        }
                    } else {
                        showToast("데이터를 불러오는데 실패했습니다: ${response.code()}")
                        Log.e("ExpiryNotification", "서버 응답 실패: ${response.code()}")
                        showEmptyMessage()
                    }
                }

                override fun onFailure(call: Call<List<IngredientDto>>, t: Throwable) {
                    showToast("서버 연결에 실패했습니다: ${t.message}")
                    Log.e("ExpiryNotification", "서버 연결 실패", t)
                    showEmptyMessage()
                }
            })
    }

    private fun processIngredients(ingredientDtos: List<IngredientDto>) {
        val ingredients = mutableListOf<Ingredient>()

        // DTO를 Ingredient 객체로 변환
        for (dto in ingredientDtos) {
            try {
                val expiryDate = apiDateFormat.parse(dto.expiryDate)
                val purchaseDate = apiDateFormat.parse(dto.purchaseDate)

                if (expiryDate != null && purchaseDate != null) {
                    ingredients.add(
                        Ingredient(
                            id = dto.id,
                            name = dto.name,
                            location = dto.location,
                            expiryDate = expiryDate,
                            purchaseDate = purchaseDate,
                            imageUrl = dto.imageUrl
                        )
                    )
                    Log.d("ExpiryNotification", "식자재 변환 성공: ${dto.name}, 소비기한: ${dto.expiryDate}")
                } else {
                    Log.e("ExpiryNotification", "날짜 파싱 실패 - ${dto.name}: 소비기한=${dto.expiryDate}, 구매일=${dto.purchaseDate}")
                }
            } catch (e: Exception) {
                Log.e("ExpiryNotification", "날짜 파싱 오류 - ${dto.name}", e)
            }
        }

        Log.d("ExpiryNotification", "변환된 식자재 총 개수: ${ingredients.size}")

        // 오늘 날짜 가져오기 (시간은 00:00:00으로 설정)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        Log.d("ExpiryNotification", "오늘 날짜: $today")

        // 3일 후 날짜 계산
        val threeDaysLater = Calendar.getInstance().apply {
            time = today
            add(Calendar.DAY_OF_MONTH, 3)
        }.time

        Log.d("ExpiryNotification", "3일 후 날짜: $threeDaysLater")

        // 표시할 식자재 필터링
        val nearExpiryAndExpiredIngredients = ingredients.filter { ingredient ->
            Log.d("ExpiryNotification", "식자재 체크: ${ingredient.name}, 소비기한: ${ingredient.expiryDate}")

            // 소비기한이 지난 경우
            val isExpired = ingredient.expiryDate.before(today)

            // 소비기한이 오늘인 경우 (같은 날)
            val isToday = isSameDay(ingredient.expiryDate, today)

            // 소비기한이 3일 이내인 경우 (오늘 이후 ~ 3일 후까지)
            val isNearExpiry = ingredient.expiryDate.after(today) &&
                    !ingredient.expiryDate.after(threeDaysLater)

            val shouldShow = isExpired || isToday || isNearExpiry

            Log.d("ExpiryNotification", "${ingredient.name} - 만료됨: $isExpired, 오늘: $isToday, 임박: $isNearExpiry, 표시여부: $shouldShow")

            shouldShow
        }

        Log.d("ExpiryNotification", "필터링된 식자재 개수: ${nearExpiryAndExpiredIngredients.size}")

        // UI 업데이트는 메인 스레드에서 실행
        runOnUiThread {
            container.removeAllViews() // 기존 뷰 제거

            if (nearExpiryAndExpiredIngredients.isEmpty()) {
                showEmptyMessage()
            } else {
                // 소비기한 순으로 정렬 (가장 임박한 것부터)
                nearExpiryAndExpiredIngredients
                    .sortedBy { it.expiryDate }
                    .forEach { ingredient ->
                        addIngredientCard(ingredient)
                    }
            }
        }
    }

    // 같은 날짜인지 확인하는 헬퍼 함수
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun showEmptyMessage() {
        val emptyView = TextView(this).apply {
            text = "소비기한이 임박하거나 지난 식자재가 없습니다."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(
                16.dpToPx(this@ExpiryNotificationActivity),
                50.dpToPx(this@ExpiryNotificationActivity),
                16.dpToPx(this@ExpiryNotificationActivity),
                0
            )
            setTextColor(Color.GRAY)
        }
        container.addView(emptyView)
    }

    private fun addIngredientCard(ingredient: Ingredient) {
        Log.d("ExpiryNotification", "카드 추가: ${ingredient.name}")

        // 카드 뷰 생성
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    16.dpToPx(this@ExpiryNotificationActivity),
                    8.dpToPx(this@ExpiryNotificationActivity),
                    16.dpToPx(this@ExpiryNotificationActivity),
                    8.dpToPx(this@ExpiryNotificationActivity)
                )
            }
            radius = 12.dpToPx(this@ExpiryNotificationActivity).toFloat()
            cardElevation = 4.dpToPx(this@ExpiryNotificationActivity).toFloat()
            setCardBackgroundColor(Color.WHITE)
        }

        // 카드 내용 레이아웃
        val cardContent = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(this@ExpiryNotificationActivity),
                    16.dpToPx(this@ExpiryNotificationActivity),
                    16.dpToPx(this@ExpiryNotificationActivity),
                    16.dpToPx(this@ExpiryNotificationActivity))
            }
            orientation = LinearLayout.HORIZONTAL
        }

        // 식재료 아이콘 이미지뷰
        val iconImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                60.dpToPx(this@ExpiryNotificationActivity),
                60.dpToPx(this@ExpiryNotificationActivity)
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = 16.dpToPx(this@ExpiryNotificationActivity)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP

            // 이미지 설정
            if (!ingredient.imageUrl.isNullOrEmpty()) {
                val imageUrl = if (ingredient.imageUrl.startsWith("http")) {
                    ingredient.imageUrl
                } else {
                    "https://foodcare-69ae76eec1bf.herokuapp.com${ingredient.imageUrl}"
                }

                Glide.with(this@ExpiryNotificationActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.basicfood)
                    .error(R.drawable.basicfood)
                    .into(this)
            } else {
                setImageResource(R.drawable.basicfood) // 기본 이미지
            }
        }

        // 텍스트 정보를 담을 레이아웃
        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }

        // 식재료 이름 텍스트뷰
        val nameTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = ingredient.name
            textSize = 18f
            setTextColor(Color.BLACK)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }



        // 소비기한 텍스트뷰
        val expiryTextView = TextView(this).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dpToPx(this@ExpiryNotificationActivity)
            }
            layoutParams = params

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val diffDays = ((ingredient.expiryDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()

            // 소비기한 상태에 따른 메시지 설정
            text = when {
                diffDays < 0 -> {
                    val daysOverdue = Math.abs(diffDays)
                    "소비기한이 ${daysOverdue}일 지났습니다! ${ingredient.name}이(가) 포함된 레시피를 추천해 드릴까요?"
                }
                diffDays == 0 -> {
                    "소비기한이 오늘까지입니다! ${ingredient.name}이(가) 포함된 레시피를 추천해 드릴까요?"
                }
                else -> {
                    "소비기한이 ${diffDays}일 남았습니다! ${ingredient.name}이(가) 포함된 레시피를 추천해 드릴까요?"
                }
            }

            // 색상 및 배경 설정
            when {
                diffDays < 0 -> {
                    setBackgroundResource(R.drawable.expiry_background) // 빨간색 배경
                    setTextColor(Color.WHITE)
                }
                diffDays == 0 -> {
                    setBackgroundResource(R.drawable.expiry_background) // 빨간색 배경
                    setTextColor(Color.WHITE)
                }
                diffDays <= 3 -> {
                    setBackgroundResource(R.drawable.expiry_warning_background) // 주황색 배경
                    setTextColor(Color.WHITE)
                }
                else -> {
                    setBackgroundColor(Color.TRANSPARENT)
                    setTextColor(Color.GRAY)
                }
            }

            setPadding(
                12.dpToPx(this@ExpiryNotificationActivity),
                6.dpToPx(this@ExpiryNotificationActivity),
                12.dpToPx(this@ExpiryNotificationActivity),
                6.dpToPx(this@ExpiryNotificationActivity)
            )

            textSize = 14f
        }

        // 위젯 추가
        textContainer.addView(nameTextView)
        textContainer.addView(expiryTextView)

        cardContent.addView(iconImageView)
        cardContent.addView(textContainer)

        cardView.addView(cardContent)

        // 카드 클릭 이벤트 추가 - 식자재를 기반으로 레시피 검색
        cardView.setOnClickListener {
            try {
                val intent = Intent(this, RecipeSearchActivity::class.java)
                intent.putExtra("SELECTED_INGREDIENT", ingredient.name)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("ExpiryNotification", "레시피 검색 화면 이동 실패", e)
                showToast("레시피 검색 화면을 열 수 없습니다.")
            }
        }

        container.addView(cardView)
    }

    // 확장 함수 - dp를 px로 변환
    fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}