package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class ExpenseDto(
    @SerializedName("expense_id") val id: Int = 0,
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("product_name") val productName: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("expense_date") val dateTime: String, // 서버는 expense_date로 받음
    @SerializedName("memo") val memo: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("user_id") val userId: Int
)