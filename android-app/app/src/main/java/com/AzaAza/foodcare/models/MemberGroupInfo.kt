package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class MemberGroupInfo(
    @SerializedName("group_owner_id") val groupOwnerId: Int,
    @SerializedName("group_owner_name") val groupOwnerName: String
)