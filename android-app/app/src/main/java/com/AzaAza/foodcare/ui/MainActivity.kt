package com.AzaAza.foodcare.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.adapter.BannerAdapter
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.IngredientDto
import com.AzaAza.foodcare.models.Recipe
import com.AzaAza.foodcare.models.RecipeDto
import com.AzaAza.foodcare.models.SignUpRequest
import com.AzaAza.foodcare.models.UpdateFcmTokenRequest
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.messaging.FirebaseMessaging
import com.AzaAza.foodcare.data.UserSession
import com.AzaAza.foodcare.models.HealthInfoResponse

class MainActivity : AppCompatActivity() {

    private lateinit var bannerPager: ViewPager2
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var isUserInteracting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)  // 다크모드 무시

        fetchRecommendedRecipes()

        // 알림 권한 확인 및 요청 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, notificationPermission) !=
                PackageManager.PERMISSION_GRANTED) {
                // 권한이 없으면 요청
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(notificationPermission),
                    100 // 요청 코드
                )
            }
        }

        // 상태 바 색상 변경 (Android 11 이상에서만 적용)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false) // Edge-to-edge 활성화
            window.statusBarColor = ContextCompat.getColor(this, R.color.your_background_color)
        }

        // 상태 바 아이콘 색상 변경 (배경이 어두우면 false, 밝으면 true)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false // false = 흰색 아이콘, true = 검은색 아이콘


        // ViewPager2 설정
        bannerPager = findViewById(R.id.bannerPager)

        val banners = listOf(
            R.drawable.six_banner,
            R.drawable.two_banner,
            R.drawable.three_banner,
            R.drawable.four_banner,
            R.drawable.five_banner,

            R.drawable.seven_banner,
            R.drawable.eight_banner
        )

        val adapter = BannerAdapter(banners) { position ->
            val targetActivity = when (position) {
                0 -> ExpenseAnalysisActivity::class.java
                1 -> UserInfoShowActivity::class.java
                2 -> MemberActivity::class.java
                3 -> FoodManagementActivity::class.java
                4 -> RecipeSearchActivity::class.java
                5 -> ExpenseActivity::class.java
                6 -> AddRecipeActivity::class.java
                else -> null
            }
            targetActivity?.let { activityClass ->
                startActivity(Intent(this, activityClass))
            }
        }

        bannerPager.adapter = adapter


        val indicator = findViewById<DotsIndicator>(R.id.dots_indicator)
        indicator.setViewPager2(bannerPager)

        // 자동 슬라이드 기능
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (!isUserInteracting) {
                    val nextItem = (bannerPager.currentItem + 1) % banners.size
                    bannerPager.currentItem = nextItem
                }
                handler.postDelayed(this, 5000) // 5초마다 실행
            }
        }
        handler.postDelayed(runnable, 5000)

        // 사용자가 손으로 슬라이드하면 자동 슬라이드 일시 중지
        bannerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> isUserInteracting = true
                    ViewPager2.SCROLL_STATE_IDLE -> isUserInteracting = false
                }
            }
        })

        // 버튼들 클릭 이벤트
        findViewById<ImageButton>(R.id.btnFoodMgmt).setOnClickListener {
            startActivity(Intent(this, FoodManagementActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnRecipeSearch).setOnClickListener {
            startActivity(Intent(this, RecipeSearchActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnExpenseAnalysis).setOnClickListener {
            startActivity(Intent(this, ExpenseAnalysisActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnExpense).setOnClickListener {
            startActivity(Intent(this, ExpenseActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnUserInfo).setOnClickListener {
            startActivity(Intent(this, UserInfoShowActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnMembers).setOnClickListener {
            startActivity(Intent(this, MemberActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnAddRecipe).setOnClickListener {
            startActivity(Intent(this, AddRecipeActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSetting).setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }


        // 알림 벨 아이콘 클릭 이벤트 추가
        val notificationBell: ImageView = findViewById(R.id.imageView) // 벨 아이콘 ID
        notificationBell.setOnClickListener {
            startActivity(Intent(this, ExpiryNotificationActivity::class.java))
        }

        // 소비기한 알림 배지 업데이트
        updateNotificationBadge()


        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val loginId = prefs.getString("USER_LOGIN_ID", null)
        val welcomeTextView = findViewById<TextView>(R.id.textView2)

        if (loginId == null) {
            welcomeTextView.text = "이름없음 님 반가워요!"
        } else {
            RetrofitClient.userApiService.getUserListAsSignUpRequest().enqueue(object : Callback<List<SignUpRequest>> {

                override fun onResponse(
                    call: Call<List<SignUpRequest>>,
                    response: Response<List<SignUpRequest>>
                ) {
                    if (response.isSuccessful) {
                        val user = response.body()?.find { it.login_id == loginId }
                        val username = user?.username ?: "이름없음"
                        welcomeTextView.text = "$username 님 반가워요!"

                        // 클릭 시 프로필 이동
                        welcomeTextView.setOnClickListener {
                            startActivity(Intent(this@MainActivity, ProfileSettingActivity::class.java))
                        }
                    }
                }

                override fun onFailure(call: Call<List<SignUpRequest>>, t: Throwable) {
                    welcomeTextView.text = "이름없음 님 반가워요!"
                }
            })
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "로그인 직후 강제 토큰 전송: $token, loginId=$loginId")
                if (loginId != null) {
                    val req = UpdateFcmTokenRequest(login_id = loginId, fcm_token = token)
                    RetrofitClient.userApiService.updateFcmToken(req)
                        .enqueue(object : retrofit2.Callback<Void> {
                            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                                Log.d("FCM", "서버로 FCM 토큰 저장 성공")
                            }
                            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                                Log.e("FCM", "서버로 FCM 토큰 저장 실패: ${t.message}")
                            }
                        })
                }
            }
        }
        if (intent.getBooleanExtra("goToMember", false)) {
            startActivity(Intent(this, MemberActivity::class.java))
        }

    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) { // 알림 권한 요청 코드
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "알림 권한이 허용되었습니다")
            } else {
                Log.d("MainActivity", "알림 권한이 거부되었습니다")
                Toast.makeText(
                    this,
                    "알림 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateNotificationBadge() {
        val badgeImage: ImageView = findViewById(R.id.imageView4)
        val badgeText: TextView = findViewById(R.id.textView3)

        // 현재 사용자 ID 가져오기
        val currentUserId = UserSession.getUserId(this)
        if (currentUserId == -1) {
            // 로그인되지 않은 상태
            badgeImage.visibility = View.GONE
            badgeText.visibility = View.GONE
            return
        }

        // 현재 사용자의 식자재 데이터만 가져와서 소비기한이 임박한 항목 카운트
        RetrofitClient.ingredientApiService.getIngredients(currentUserId)
            .enqueue(object : Callback<List<IngredientDto>> {
                override fun onResponse(call: Call<List<IngredientDto>>, response: Response<List<IngredientDto>>) {
                    if (response.isSuccessful) {
                        val ingredients = response.body()
                        if (ingredients != null) {
                            // 한번 더 사용자 필터링 (보안상)
                            val userIngredients = ingredients.filter { it.userId == currentUserId }

                            // 오늘 날짜 설정
                            val today = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.time

                            val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                            // 1. 소비기한이 지난 항목
                            val expiredCount = userIngredients.count { ingredient ->
                                try {
                                    val expiryDate = apiDateFormat.parse(ingredient.expiryDate) ?: return@count false
                                    expiryDate.before(today)
                                } catch (e: Exception) {
                                    false
                                }
                            }

                            // 2. 소비기한이 3일 이내인 항목 (오늘 포함)
                            val nearExpiryCount = userIngredients.count { ingredient ->
                                try {
                                    val expiryDate = apiDateFormat.parse(ingredient.expiryDate) ?: return@count false
                                    val diffDays = ((expiryDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
                                    diffDays in 0..3 && !expiryDate.before(today)
                                } catch (e: Exception) {
                                    false
                                }
                            }

                            // 총 알림 개수
                            val totalNotificationCount = expiredCount + nearExpiryCount

                            // 뱃지 표시 로직
                            if (totalNotificationCount > 0) {
                                badgeImage.visibility = View.VISIBLE
                                badgeText.visibility = View.VISIBLE
                                badgeText.text = totalNotificationCount.toString()
                            } else {
                                badgeImage.visibility = View.GONE
                                badgeText.visibility = View.GONE
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<List<IngredientDto>>, t: Throwable) {
                    Log.e("MainActivity", "서버 연결 실패", t)
                    badgeImage.visibility = View.GONE
                    badgeText.visibility = View.GONE
                }
            })
    }

    // 액티비티 재개 시 데이터와 뱃지 업데이트
    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

    // 액티비티 종료 시 자동 슬라이드 정지
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
    private fun fetchRecommendedRecipes() {
        val recommendationsContainer = findViewById<LinearLayout>(R.id.foodRecommendationsContainer)
        recommendationsContainer.removeAllViews()

        RetrofitClient.recipeApiService.getRecipes().enqueue(object : Callback<List<RecipeDto>> {
            override fun onResponse(
                call: Call<List<RecipeDto>>,
                response: Response<List<RecipeDto>>
            ) {
                if (response.isSuccessful) {
                    val recipeDtos = response.body() ?: return

                    val randomRecipes = recipeDtos.shuffled().take(5)
                    for (dto in randomRecipes) {
                        val recipe = dto.toRecipe(emptyList())
                        val cardView = layoutInflater.inflate(R.layout.item_today_recommendation, recommendationsContainer, false)

                        val imageView = cardView.findViewById<ImageView>(R.id.recipeImage)

                        val nameText = cardView.findViewById<TextView>(R.id.recipeName)
                        val summaryText = cardView.findViewById<TextView>(R.id.recipeSummary)
                        val ingredientsText = cardView.findViewById<TextView>(R.id.recipeIngredients)
                        val btnDetail = cardView.findViewById<Button>(R.id.btnViewRecipe)

                        imageView.setImageResource(recipe.imageResId)
                        nameText.text = recipe.name
                        summaryText.text = recipe.summary ?: "" // 한 줄 소개 표시
                        ingredientsText.text = "재료: " + recipe.ingredients.joinToString(", ")

                        btnDetail.setOnClickListener {
                            val intent = Intent(cardView.context, RecipeDetailActivity::class.java)
                            intent.putExtra("EXTRA_RECIPE", recipe)
                            // 필요하다면 사용자 식자재, 건강정보도 전달
                            intent.putStringArrayListExtra("EXTRA_MY_INGREDIENTS", ArrayList<String>())
                            intent.putExtra("EXTRA_USER_HEALTH", null as? HealthInfoResponse)
                            cardView.context.startActivity(intent)
                        }


                        recommendationsContainer.addView(cardView)
                    }

                    val moreBtn = Button(this@MainActivity).apply {
                        text = "더 많은 레시피 확인하기!"
                        setBackgroundTintList(ContextCompat.getColorStateList(this@MainActivity, R.color.green_700))
                        setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                        textSize = 15f
                        setPadding(40, 18, 40, 18)
                        background = ContextCompat.getDrawable(this@MainActivity, R.drawable.rounded_button_bg) // 선택
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 36
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                        layoutParams = params

                        setOnClickListener {
                            startActivity(Intent(this@MainActivity, RecipeSearchActivity::class.java))
                        }
                    }
                    recommendationsContainer.addView(moreBtn)

                }
            }


            override fun onFailure(call: Call<List<RecipeDto>>, t: Throwable) {

            }
        })
    }

    // RecipeAdapter의 상세 다이얼로그 로직 복붙 또는 함수로 분리해서 사용
    private fun showRecipeDetailDialog(recipe: Recipe, context: Context) {
        val message = """
📝 레시피 설명:
${recipe.instructions}

🧂 필요한 재료:
${recipe.ingredients.joinToString(", ")}

⏱ 소요 시간: ${recipe.timeTaken ?: "알 수 없음"}
💪 난이도: ${recipe.difficulty ?: "알 수 없음"}
🩺 알레르기: ${recipe.allergies ?: "없음"}
🚫 질병 관련: ${recipe.disease ?: "없음"}
""".trimIndent()

        val textView = TextView(context).apply {
            text = message
            textSize = 16f
            setPadding(40, 40, 40, 40)
            isVerticalScrollBarEnabled = true
            movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val scrollView = ScrollView(context).apply {
            addView(textView)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        AlertDialog.Builder(context)
            .setTitle("${recipe.name} 상세 정보")
            .setView(scrollView)
            .setPositiveButton("닫기", null)
            .show()
    }

}