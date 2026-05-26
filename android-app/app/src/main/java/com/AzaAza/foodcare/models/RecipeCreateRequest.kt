package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class RecipeCreateRequest(
    @SerializedName("name") val name: String,
    @SerializedName("summary") val summary: String,
    @SerializedName("ingredients") val ingredients: String,
    @SerializedName("instructions") val instructions: String,
    @SerializedName("timetaken") val timetaken: String,
    @SerializedName("difficultylevel") val difficultylevel: String,
    @SerializedName("allergies") val allergies: String,
    @SerializedName("disease") val disease: String,
    @SerializedName("diseasereason") val diseasereason: String?,
    @SerializedName("category") val category: String,
    @SerializedName("image_url") val imageUrl: String? = null  // 🔥 이미지 URL 필드 추가
)