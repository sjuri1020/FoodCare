package com.AzaAza.foodcare.models

data class RecipeCreateResponse(
    val success: Boolean,
    val message: String,
    val recipeId: Int? = null
)