package com.AzaAza.foodcare.models

import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Parcelize
data class Allergen(val id: Int, val code: String, val name: String): Parcelable