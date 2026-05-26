package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class OwnerGroupInfo(
    @SerializedName("group_owner_id") val groupOwnerId: Int,
    @SerializedName("group_owner_name") val groupOwnerName: String,
    @SerializedName("member_id") val memberId: Int,
    @SerializedName("member_name") val memberName: String
)