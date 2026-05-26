package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class CategorySummary(
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("percentage") val percentage: Double
)