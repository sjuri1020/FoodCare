package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class CategoryDto(
    @SerializedName("category_id") val id: Int,  // 서버의 category_id를 id로 매핑
    @SerializedName("category_name") val name: String,  // 서버의 category_name을 name으로 매핑
    @SerializedName("user_id") val userId: Int,
    var totalAmount: Double = 0.0
)