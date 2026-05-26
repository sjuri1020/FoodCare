// IngredientApiService.kt
package com.AzaAza.foodcare.api

import com.AzaAza.foodcare.models.IngredientDto
import com.AzaAza.foodcare.models.IngredientResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

interface IngredientApiService {

    // 식자재 추가
    @Multipart
    @POST("/ingredients")
    fun addIngredient(
        @Part("name") name: RequestBody,
        @Part("location") location: RequestBody,
        @Part("expiry_date") expiryDate: RequestBody,
        @Part("purchase_date") purchaseDate: RequestBody,
        @Part("user_id") userId: RequestBody,
        @Part image: MultipartBody.Part?  // 이미지 없으면 null 넣으면 됨
    ): Call<IngredientResponse>

    // 내가 등록한 식자재만 조회 (개인 모드)
    @GET("/ingredients")
    fun getIngredients(
        @Query("user_id") userId: Int
    ): Call<List<IngredientDto>>

    // 공유 모드일 때 전체 그룹 식자재 조회
    @GET("/ingredients/shared/{owner_id}")
    fun getSharedIngredients(
        @Path("owner_id") ownerId: Int
    ): Call<List<IngredientDto>>

    // 식자재 삭제 (등록한 사람만 삭제 가능)
    @DELETE("/ingredients/{id}")
    fun deleteIngredient(
        @Path("id") id: Int,
        @Query("user_id") userId: Int
    ): Call<IngredientResponse>
}
