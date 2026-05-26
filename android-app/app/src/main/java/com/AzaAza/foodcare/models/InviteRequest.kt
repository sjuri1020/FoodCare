package com.AzaAza.foodcare.models

data class InviteRequest(
    val owner_id: Int,
    val member_login_id: String
)