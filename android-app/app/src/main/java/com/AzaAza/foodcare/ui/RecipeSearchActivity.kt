package com.AzaAza.foodcare.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.adapter.RecipeAdapter
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.helper.RecipeSearchHelper
import com.AzaAza.foodcare.models.IngredientDto
import com.AzaAza.foodcare.models.Recipe
import com.AzaAza.foodcare.models.RecipeDto
import com.AzaAza.foodcare.data.UserSession
import com.AzaAza.foodcare.models.HealthInfoResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RecipeSearchActivity : AppCompatActivity() {
    private lateinit var recipeAdapter: RecipeAdapter
    private lateinit var recipeRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchEditText: EditText
    private lateinit var selectedIngredientLabel: TextView
    private var allRecipes = listOf<Recipe>()
    private var userIngredients = listOf<String>()
    private var selectedIngredient: String? = null
    private var userHealthInfo: HealthInfoResponse? = null

    // 현재 로그인한 사용자 ID
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_search)

        // 현재 사용자 ID 가져오기
        currentUserId = UserSession.getUserId(this)

        // 로그인 상태 확인
        if (currentUserId == -1 || !UserSession.isLoggedIn(this)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 선택된 식자재가 있는지 확인
        selectedIngredient = intent.getStringExtra("SELECTED_INGREDIENT")

        // 뒤로가기 버튼
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        // 프로그레스바 설정
        progressBar = findViewById(R.id.progressBar)

        // 선택된 식자재 라벨 설정
        selectedIngredientLabel = findViewById(R.id.selectedIngredientLabel)
        if (selectedIngredient != null) {
            selectedIngredientLabel.text = "선택된 식자재: $selectedIngredient"
            selectedIngredientLabel.visibility = View.VISIBLE
        } else {
            selectedIngredientLabel.visibility = View.GONE
        }

        // 검색바 설정
        searchEditText = findViewById(R.id.searchEditText)

        // 선택된 식자재가 있으면 검색창에 표시
        if (!selectedIngredient.isNullOrEmpty()) {
            searchEditText.setText(selectedIngredient)
        }

        searchEditText.addTextChangedListener { text ->
            filterRecipes(text.toString())
        }

        // RecyclerView 설정
        recipeRecyclerView = findViewById(R.id.recipeRecyclerView)
        recipeRecyclerView.layoutManager = LinearLayoutManager(this)
        recipeAdapter = RecipeAdapter(allRecipes, userIngredients, userHealthInfo)
        recipeRecyclerView.adapter = recipeAdapter

        fetchUserHealthInfo()
    }

    private fun fetchUserHealthInfo() {
        RetrofitClient.userInfoApiService.listHealthInfo(currentUserId)
            .enqueue(object : Callback<List<HealthInfoResponse>> {
                override fun onResponse(
                    call: Call<List<HealthInfoResponse>>,
                    response: Response<List<HealthInfoResponse>>
                ) {
                    userHealthInfo = response.body()?.firstOrNull()
                    fetchUserIngredients()  // 식자재는 여기서만 시작!
                }
                override fun onFailure(call: Call<List<HealthInfoResponse>>, t: Throwable) {
                    fetchUserIngredients()
                }
            })
    }

    /**
     * 현재 사용자의 식자재 목록만 가져오기
     */
    private fun fetchUserIngredients() {
        progressBar.visibility = View.VISIBLE

        // 현재 사용자의 식자재만 조회
        RetrofitClient.ingredientApiService.getIngredients(currentUserId)
            .enqueue(object : Callback<List<IngredientDto>> {
                override fun onResponse(
                    call: Call<List<IngredientDto>>,
                    response: Response<List<IngredientDto>>
                ) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val ingredients = response.body()
                        if (ingredients != null) {
                            // 한번 더 사용자 필터링 (보안상)
                            val userIngredientsData = ingredients.filter { it.userId == currentUserId }
                            userIngredients = userIngredientsData.map { it.name }

                            // 사용자의 식자재 목록을 가져온 후 레시피 데이터 가져오기
                            fetchRecipesFromServer()
                        } else {
                            Toast.makeText(
                                this@RecipeSearchActivity,
                                "재료 목록을 불러오는데 실패했습니다: 빈 응답",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@RecipeSearchActivity,
                            "재료 목록을 불러오는데 실패했습니다: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<IngredientDto>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@RecipeSearchActivity,
                        "서버 연결에 실패했습니다: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    /**
     * 서버에서 레시피 데이터 가져오기
     */
    private fun fetchRecipesFromServer() {
        progressBar.visibility = View.VISIBLE

        RetrofitClient.recipeApiService.getRecipes().enqueue(object : Callback<List<RecipeDto>> {
            override fun onResponse(
                call: Call<List<RecipeDto>>,
                response: Response<List<RecipeDto>>
            ) {
                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val recipeDtos = response.body()
                    if (recipeDtos != null) {
                        // DTO를 Recipe 객체로 변환하고 일치 재료 개수로 정렬
                        allRecipes = recipeDtos.map { it.toRecipe(userIngredients) }
                            .sortedByDescending { it.matchedCount }

                        // RecipeAdapter에 사용자 식자재 정보 전달
                        recipeAdapter = RecipeAdapter(allRecipes, userIngredients, userHealthInfo)
                        recipeRecyclerView.adapter = recipeAdapter

                        // 선택된 식자재로 초기 필터링 수행
                        if (!selectedIngredient.isNullOrEmpty()) {
                            filterRecipes(selectedIngredient!!)
                        } else {
                            // 선택된 식자재가 없으면 전체 레시피 표시 (매칭 순으로 정렬됨)
                            recipeAdapter.updateList(allRecipes)
                        }

                        // 성공 메시지 표시
                        val matchedRecipesCount = allRecipes.count { it.matchedCount > 0 }
                        val totalRecipesCount = allRecipes.size

                        if (userIngredients.isNotEmpty()) {
                            Toast.makeText(
                                this@RecipeSearchActivity,
                                "총 ${totalRecipesCount}개 레시피 중 ${matchedRecipesCount}개가 보유 재료와 일치합니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@RecipeSearchActivity,
                                "${totalRecipesCount}개의 레시피를 가져왔습니다. 식자재를 등록하시면 맞춤 추천을 받을 수 있습니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@RecipeSearchActivity,
                            "레시피 데이터를 불러오는데 실패했습니다: 빈 응답",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@RecipeSearchActivity,
                        "레시피 데이터를 불러오는데 실패했습니다: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<RecipeDto>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@RecipeSearchActivity,
                    "서버 연결에 실패했습니다: ${t.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    /**
     * 개선된 레시피 필터링 및 검색 기능
     */
    private fun filterRecipes(query: String) {
        if (query.isEmpty() && selectedIngredient.isNullOrEmpty()) {
            // 검색어와 선택된 식자재가 모두 없으면 전체 레시피 표시 (매칭 순으로 정렬)
            recipeAdapter.updateList(allRecipes)
            selectedIngredientLabel.visibility = View.GONE
            return
        }

        val queryToUse = if (query.isNotEmpty()) query else selectedIngredient ?: ""

        // 1. 레시피 이름으로 검색
        val nameFiltered = RecipeSearchHelper.filter(queryToUse, allRecipes)

        // 2. 재료명으로 검색 (개선된 매칭 로직 적용)
        val ingredientFiltered = allRecipes.filter { recipe ->
            hasMatchingIngredient(recipe, queryToUse)
        }

        // 3. 카테고리로 검색
        val categoryFiltered = allRecipes.filter { recipe ->
            recipe.category?.contains(queryToUse, ignoreCase = true) == true
        }

        // 4. 난이도로 검색
        val difficultyFiltered = allRecipes.filter { recipe ->
            recipe.difficulty?.contains(queryToUse, ignoreCase = true) == true
        }

        // 5. 소요시간으로 검색
        val timeFiltered = allRecipes.filter { recipe ->
            recipe.timeTaken?.contains(queryToUse, ignoreCase = true) == true
        }

        // 모든 검색 결과 합치기 (중복 제거)
        val allFilteredResults = (nameFiltered + ingredientFiltered + categoryFiltered + difficultyFiltered + timeFiltered).distinct()

        // 선택된 식자재가 있는 경우, 해당 식자재를 포함하지 않은 레시피는 제외
        val finalFilteredResults = if (!selectedIngredient.isNullOrEmpty() && query == selectedIngredient) {
            allFilteredResults.filter { recipe ->
                hasMatchingIngredient(recipe, selectedIngredient!!)
            }
        } else {
            allFilteredResults
        }

        // 매칭된 재료 개수와 관련성에 따라 정렬
        val sortedResults = finalFilteredResults.sortedWith(compareByDescending<Recipe> { recipe ->
            // 1순위: 보유 재료와의 매칭 개수
            recipe.matchedCount
        }.thenByDescending { recipe ->
            // 2순위: 검색어와 레시피 이름의 관련성
            when {
                recipe.name.equals(queryToUse, ignoreCase = true) -> 100 // 완전 일치
                recipe.name.contains(queryToUse, ignoreCase = true) -> 50 // 부분 일치
                else -> 0
            }
        }.thenByDescending { recipe ->
            // 3순위: 검색어와 재료의 관련성 (개선된 매칭 고려)
            getIngredientMatchScore(recipe, queryToUse)
        })

        recipeAdapter.updateList(sortedResults)

        // 선택된 식자재 표시 라벨 업데이트
        updateSelectedIngredientLabel(query, queryToUse, sortedResults.size)
    }

    /**
     * 레시피가 특정 재료와 매칭되는지 확인 (개선된 로직)
     */
    private fun hasMatchingIngredient(recipe: Recipe, queryIngredient: String): Boolean {
        return recipe.ingredients.any { recipeIngredient ->
            isIngredientMatched(recipeIngredient, queryIngredient)
        }
    }

    /**
     * 재료 매칭 점수 계산
     */
    private fun getIngredientMatchScore(recipe: Recipe, queryIngredient: String): Int {
        var score = 0
        recipe.ingredients.forEach { recipeIngredient ->
            if (isIngredientMatched(recipeIngredient, queryIngredient)) {
                score += when {
                    recipeIngredient.equals(queryIngredient, ignoreCase = true) -> 10 // 완전 일치
                    recipeIngredient.contains(queryIngredient, ignoreCase = true) -> 5 // 부분 일치
                    else -> 2 // 대체재 매칭
                }
            }
        }
        return score
    }

    /**
     * 개선된 재료 매칭 로직
     */
    private fun isIngredientMatched(recipeIngredient: String, queryIngredient: String): Boolean {
        val cleanRecipeIngredient = recipeIngredient.trim()
        val cleanQueryIngredient = queryIngredient.trim()

        // 1. 직접 매칭 (대소문자 무시)
        if (cleanRecipeIngredient.equals(cleanQueryIngredient, ignoreCase = true)) {
            return true
        }

        // 2. 부분 매칭
        if (cleanRecipeIngredient.contains(cleanQueryIngredient, ignoreCase = true) ||
            cleanQueryIngredient.contains(cleanRecipeIngredient, ignoreCase = true)) {
            return true
        }

        // 3. 괄호 처리: "돼지고기(또는 참치)" 같은 경우
        if (cleanRecipeIngredient.contains("(") && cleanRecipeIngredient.contains(")")) {
            val alternatives = extractAlternatives(cleanRecipeIngredient)
            return alternatives.any { alternative ->
                alternative.equals(cleanQueryIngredient, ignoreCase = true) ||
                        alternative.contains(cleanQueryIngredient, ignoreCase = true) ||
                        cleanQueryIngredient.contains(alternative, ignoreCase = true)
            }
        }

        return false
    }

    /**
     * 괄호 안의 대체 재료들을 추출
     * 예: "돼지고기(또는 참치)" -> ["돼지고기", "참치"]
     */
    private fun extractAlternatives(ingredient: String): List<String> {
        val alternatives = mutableListOf<String>()

        // 괄호 밖의 주 재료 추가
        val mainIngredient = ingredient.substringBefore("(").trim()
        if (mainIngredient.isNotEmpty()) {
            alternatives.add(mainIngredient)
        }

        // 괄호 안의 내용 처리
        val parenthesesContent = ingredient.substringAfter("(").substringBefore(")").trim()
        if (parenthesesContent.isNotEmpty()) {
            // "또는", "혹은", "," 등으로 분리
            val separators = listOf("또는", "혹은", "이나", ",", "/", "｜")
            var content = parenthesesContent

            for (separator in separators) {
                if (content.contains(separator)) {
                    val parts = content.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
                    alternatives.addAll(parts)
                    break
                }
            }

            // 분리되지 않았다면 전체를 하나의 대체재로 추가
            if (!separators.any { content.contains(it) }) {
                alternatives.add(content)
            }
        }

        return alternatives.distinct().filter { it.isNotEmpty() }
    }

    /**
     * 선택된 식자재/검색어 라벨 업데이트
     */
    private fun updateSelectedIngredientLabel(query: String, queryToUse: String, resultCount: Int) {
        when {
            query.isNotEmpty() -> {
                selectedIngredientLabel.text = "검색: '$query' (${resultCount}개 결과)"
                selectedIngredientLabel.visibility = View.VISIBLE
            }
            !selectedIngredient.isNullOrEmpty() -> {
                selectedIngredientLabel.text = "선택된 식자재: '$selectedIngredient' (${resultCount}개 결과)"
                selectedIngredientLabel.visibility = View.VISIBLE
            }
            else -> {
                selectedIngredientLabel.visibility = View.GONE
            }
        }
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