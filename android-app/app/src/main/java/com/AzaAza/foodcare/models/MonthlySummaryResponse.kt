package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class MonthlySummaryResponse(
    @SerializedName("year") val year: Int,
    @SerializedName("month") val month: Int,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("categories") val categories: List<CategorySummary>
)