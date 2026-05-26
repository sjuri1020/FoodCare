package com.AzaAza.foodcare.models

import kotlinx.parcelize.Parcelize
import android.os.Parcelable


@Parcelize
data class Recipe(
    val name: String,
    val summary: String? = null,
    val description: String,// 요약
    val instructions: String,// 전체 조리 순서
    val imageResId: Int,  // 이미지 리소스 ID
    val imageUrl: String?,
    val ingredients: List<String>,
    val matchedCount: Int = 0,
    val matchedIngredients: List<String> = emptyList(),
    val timeTaken: String? = null,
    val difficulty: String? = null,
    val allergies: String? = null,
    val disease: String? = null,
    val diseaseReason: String? = null,
    val category: String? = null
): Parcelable