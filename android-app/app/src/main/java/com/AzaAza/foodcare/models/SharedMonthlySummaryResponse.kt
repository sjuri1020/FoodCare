package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class SharedMonthlySummaryResponse(
    @SerializedName("year") val year: Int,
    @SerializedName("month") val month: Int,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("categories") val categories: List<CategorySummary>,
    @SerializedName("group_owner_id") val groupOwnerId: Int,
    @SerializedName("group_owner_name") val groupOwnerName: String,
    @SerializedName("member_count") val memberCount: Int
)
