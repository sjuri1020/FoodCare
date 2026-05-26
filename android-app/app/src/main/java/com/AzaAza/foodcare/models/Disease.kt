package com.AzaAza.foodcare.models

import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Parcelize
data class Disease(val id: Int, val code: String, val name: String): Parcelable