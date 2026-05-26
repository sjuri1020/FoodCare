package com.AzaAza.foodcare.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.models.Recipe
import com.AzaAza.foodcare.models.HealthInfoResponse
import com.bumptech.glide.Glide

class RecipeDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)

        val backButton = findViewById<ImageView>(R.id.backButton)
        backButton.setOnClickListener { finish() }

        val recipe = intent.getParcelableExtra<Recipe>("EXTRA_RECIPE")
        val userIngredients = intent.getStringArrayListExtra("EXTRA_MY_INGREDIENTS") ?: arrayListOf()
        val userHealthInfo = intent.getParcelableExtra<HealthInfoResponse>("EXTRA_USER_HEALTH")

        // 대표 이미지
        val recipeImage = findViewById<ImageView>(R.id.ivRecipeImage)
        val baseUrl = "https://foodcare-69ae76eec1bf.herokuapp.com"
        if (!recipe?.imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(baseUrl + recipe!!.imageUrl)
                .placeholder(recipe.imageResId) // 이게 네가 매핑한 그 이미지!
                .error(recipe.imageResId)       // 실패시에도 동일
                .into(recipeImage)
        } else {
            recipeImage.setImageResource(recipe?.imageResId ?: R.drawable.no_img)
        }

        // 레시피명, 요약
        findViewById<TextView>(R.id.tvRecipeName).text = recipe?.name ?: ""
        findViewById<TextView>(R.id.tvSummary).text = recipe?.summary ?: ""

        // 내가 가진/없는 재료
        val allIng = recipe?.ingredients ?: emptyList()
        val have = allIng.filter { userIngredients.contains(it) }
        val notHave = allIng.filter { !userIngredients.contains(it) }
        findViewById<TextView>(R.id.tvIngredientInfo).text =
            "내가 가진 재료: ${have.joinToString(", ")}\n없는 재료: ${notHave.joinToString(", ")}"

        // 모든 정보(시간, 난이도, 카테고리, 알레르기, 질병, 이유 등)
        val allInfo = """
⏱ 소요 시간: ${recipe?.timeTaken ?: "-"}
💪 난이도: ${recipe?.difficulty ?: "-"}
📦 카테고리: ${recipe?.category ?: "-"}
🩺 알레르기: ${recipe?.allergies ?: "-"}
🚫 질병 관련: ${recipe?.disease ?: "-"}
💬 이유: ${recipe?.diseaseReason ?: "-"}
        """.trimIndent()
        findViewById<TextView>(R.id.tvAllInfo).text = allInfo

        // 레시피 설명
        findViewById<TextView>(R.id.tvRecipeDesc).text = recipe?.instructions ?: "-"

        // 건강 안내
        findViewById<TextView>(R.id.tvHealthInfo).text =
            getHealthReason(recipe, userHealthInfo)
    }

    private fun getHealthReason(recipe: Recipe?, userInfo: HealthInfoResponse?): String {
        if (recipe == null || userInfo == null) return ""
        val userAllergens = userInfo.allergens.map { it.name }
        val recipeAllergies = recipe.allergies?.split(",")?.map { it.trim() } ?: emptyList()
        val allergenMatched = userAllergens.intersect(recipeAllergies.toSet())

        val userDiseases = userInfo.diseases.map { it.name }
        val recipeDiseases = recipe.disease?.split(",")?.map { it.trim() } ?: emptyList()
        val diseaseMatched = userDiseases.intersect(recipeDiseases.toSet())

        val reasons = mutableListOf<String>()
        if (allergenMatched.isNotEmpty())
            reasons += "⚠️ [${allergenMatched.joinToString(", ")}] 알레르기 유발 재료가 포함되어 있습니다."
        if (diseaseMatched.isNotEmpty())
            reasons += "🚫 [${diseaseMatched.joinToString(", ")}] 관련 질병 주의 필요."
        if (reasons.isEmpty())
            reasons += "✅ 입력한 건강정보와 충돌 없이 섭취 가능합니다."
        return reasons.joinToString("\n")
    }
}
