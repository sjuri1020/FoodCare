package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class IngredientDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("location") val location: String,
    @SerializedName("expiry_date") val expiryDate: String,
    @SerializedName("purchase_date") val purchaseDate: String,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("user_id") val userId: Int,  // 이 필드가 중요합니다
    @SerializedName("owner_name") val ownerName: String? = null  // 공유 모드에서 소유자 이름 (선택적)
)