package com.AzaAza.foodcare.api

import com.AzaAza.foodcare.models.*
import retrofit2.Call
import retrofit2.http.*

interface UserInfoApiService {
    @GET("/allergens")
    fun getAllAllergens(): Call<List<Allergen>>

    @GET("/diseases")
    fun getAllDiseases(): Call<List<Disease>>

    @GET("/health_info")
    fun listHealthInfo(
        @Query("user_id") userId: Int
    ): Call<List<HealthInfoResponse>>


    @GET("/health_info/{id}")
    fun getHealthInfo(@Path("id") id: Int): Call<HealthInfoResponse>

    @POST("/health_info")
    fun createHealthInfo(@Body request: HealthInfoRequest): Call<HealthInfoResponse>

    @PUT("/health_info/{id}")
    fun updateHealthInfo(@Path("id") id: Int, @Body request: HealthInfoRequest): Call<HealthInfoResponse>
}