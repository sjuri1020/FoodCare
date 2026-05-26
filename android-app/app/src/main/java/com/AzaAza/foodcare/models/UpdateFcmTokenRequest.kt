package com.AzaAza.foodcare.models

data class UpdateFcmTokenRequest(
    val login_id: String,
    val fcm_token: String
)
