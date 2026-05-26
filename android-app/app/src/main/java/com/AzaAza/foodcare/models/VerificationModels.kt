package com.AzaAza.foodcare.models

data class VerificationRequestDto(
    val name: String,
    val email: String,
    val purpose: String = "findId",
    val login_id: String? = null  // 비밀번호 찾기용 추가 필드
)

data class VerificationConfirmDto(
    val email: String,
    val code: String,
    val purpose: String = "findId"
)

data class VerificationResponseDto(
    val success: Boolean,
    val message: String,
    val data: Map<String, String>? = null
)