package com.AzaAza.foodcare.models

data class HealthInfoRequest(
    val user_id: Int,
    val birth_date: String,
    val gender: String,
    val height_cm: Double,
    val weight_kg: Double,
    val food_preference: String,
    val allergen_ids: List<Int>,
    val disease_ids: List<Int>
)