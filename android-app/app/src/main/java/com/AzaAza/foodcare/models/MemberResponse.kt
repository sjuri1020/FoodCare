package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class MemberResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
    @SerializedName("login_id") val loginId: String,
    @SerializedName("email") val email: String,
    @SerializedName("status") val status: String,    // "pending" 또는 "accepted"
    @SerializedName("is_owner") val isOwner: Boolean,
    @SerializedName("profile_image_url") val profileImageUrl: String?
)