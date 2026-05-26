package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class IngredientResponse(
    @SerializedName("message") val message: String
)