package com.AzaAza.foodcare.models

data class InviteResponse(
    val success: Boolean?,
    val message: String?,
    val id: Int?,                // 초대 ID
    val owner_id: Int?,
    val owner_username: String?,
    val member_id: Int?,
    val member_username: String?,
    val status: String?,
    val created_at: String?
)
