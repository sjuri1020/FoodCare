package com.AzaAza.foodcare.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.models.Recipe
import com.AzaAza.foodcare.models.HealthInfoResponse
import com.AzaAza.foodcare.ui.RecipeDetailActivity
import com.bumptech.glide.Glide

class RecipeAdapter(
    private var recipes: List<Recipe>,
    private val userIngredients: List<String>,
    private val userHealthInfo: HealthInfoResponse?
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    inner class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.recipeName)
        val descriptionText: TextView = itemView.findViewById(R.id.recipeDescription)
        val imageView: ImageView = itemView.findViewById(R.id.recipeImage)
        val matchedCountText: TextView = itemView.findViewById(R.id.matchedCountText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recipe_item, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.nameText.text = recipe.name
        holder.descriptionText.text = recipe.description

        // 이미지 표시 우선순위: 서버 사진 > 기본 drawable
        val baseUrl = "https://foodcare-69ae76eec1bf.herokuapp.com"
        if (!recipe.imageUrl.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(baseUrl + recipe.imageUrl)
                .placeholder(recipe.imageResId) // 로딩 전엔 기존 이미지
                .error(recipe.imageResId)       // 실패시에도 기존 이미지
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(recipe.imageResId)
        }

        // 매칭된 재료 정보 표시 (개선된 로직)
        holder.matchedCountText.text = if (recipe.matchedIngredients.isNotEmpty()) {
            "일치 재료: ${recipe.matchedCount}개 (${recipe.matchedIngredients.joinToString(", ")})"
        } else {
            "일치하는 재료 없음"
        }

        // 레시피 상세 화면으로 이동
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, RecipeDetailActivity::class.java)
            intent.putExtra("EXTRA_RECIPE", recipe)
            intent.putStringArrayListExtra("EXTRA_MY_INGREDIENTS", ArrayList(userIngredients))
            intent.putExtra("EXTRA_USER_HEALTH", userHealthInfo)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = recipes.size

    fun updateList(newList: List<Recipe>) {
        recipes = newList
        notifyDataSetChanged()
    }
}