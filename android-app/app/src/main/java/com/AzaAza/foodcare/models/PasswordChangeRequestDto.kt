package com.AzaAza.foodcare.models

// 비밀번호 변경 요청 모델
data class PasswordChangeRequestDto(
    val login_id: String,
    val current_password: String,
    val new_password: String
)

// 비밀번호 변경 응답 모델
data class PasswordChangeResponseDto(
    val success: Boolean,
    val message: String
)