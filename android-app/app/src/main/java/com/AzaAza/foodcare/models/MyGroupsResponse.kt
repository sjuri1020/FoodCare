package com.AzaAza.foodcare.models

import com.google.gson.annotations.SerializedName

data class MyGroupsResponse(
    @SerializedName("as_owner") val asOwner: List<OwnerGroupInfo>,
    @SerializedName("as_member") val asMember: List<MemberGroupInfo>
)
