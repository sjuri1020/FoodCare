package com.AzaAza.foodcare.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.models.CategoryDto
import java.text.NumberFormat
import java.util.*

class CategoryAdapter(
    private val categories: List<CategoryDto>,
    private val onCategoryClick: (CategoryDto) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    companion object {
        private const val TAG = "CategoryAdapter"
    }

    private var categoryColors: List<Int> = listOf()

    // 외부에서 색상 리스트 갱신할 때 사용
    fun updateColors(colors: List<Int>) {
        Log.d(TAG, "updateColors 호출 - 색상 개수: ${colors.size}")
        categoryColors = colors
        notifyDataSetChanged()  // 색상 적용 위해 전체 새로고침
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        if (position < categories.size) {
            val category = categories[position]
            holder.bind(category, position)
        } else {
            Log.e(TAG, "잘못된 포지션: $position / 총 카테고리 수: ${categories.size}")
        }
    }

    override fun getItemCount(): Int {
        return categories.size
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryNameText: TextView = itemView.findViewById(R.id.categoryNameText)
        private val categoryAmountText: TextView = itemView.findViewById(R.id.categoryAmountText)
        private val categoryColorIndicator: View = itemView.findViewById(R.id.categoryColorIndicator)

        init {
            // 뷰 연결 확인용 로그 (디버깅 편하게 하려고 넣음)
            if (categoryNameText == null) Log.e(TAG, "categoryNameText 못 찾음")
            if (categoryAmountText == null) Log.e(TAG, "categoryAmountText 못 찾음")
            if (categoryColorIndicator == null) Log.e(TAG, "categoryColorIndicator 못 찾음")
        }

        fun bind(category: CategoryDto, position: Int) {
            try {
                // 이름 표시
                categoryNameText.text = category.name

                // 금액 포맷 설정
                val formatter = NumberFormat.getInstance(Locale.KOREA)
                val formattedAmount = "${formatter.format(category.totalAmount.toInt())}원"
                categoryAmountText.text = formattedAmount

                // 색상 인디케이터 적용 - position에 따라 색 다르게
                if (position < categoryColors.size && categoryColors.isNotEmpty()) {
                    val color = categoryColors[position]
                    categoryColorIndicator.setBackgroundResource(R.drawable.category_color_square)
                    categoryColorIndicator.background?.setTint(color)
                } else {
                    // 색상이 부족할 경우 기본 회색 적용
                    categoryColorIndicator.setBackgroundResource(R.drawable.category_color_square)
                    categoryColorIndicator.background?.setTint(0xFFAAAAAA.toInt())
                }

                // 클릭 이벤트 연결
                itemView.setOnClickListener {
                    onCategoryClick(category)
                }

                // 혹시라도 뷰가 숨겨진 상태일 수도 있어서 명시적으로 표시
                itemView.visibility = View.VISIBLE

            } catch (exception: Exception) {
                Log.e(TAG, "bind() 중 오류 발생", exception)
            }
        }
    }
}
