package com.AzaAza.foodcare.models

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val id: Int?,
    val username: String?,
    val login_id: String?,
    val email: String?
)
