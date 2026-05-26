package com.AzaAza.foodcare.api

import com.AzaAza.foodcare.models.RecipeCreateRequest
import com.AzaAza.foodcare.models.RecipeCreateResponse
import com.AzaAza.foodcare.models.RecipeDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface RecipeApiService {
    // 기존 레시피 조회
    @GET("/recipes")
    fun getRecipes(): Call<List<RecipeDto>>

    // 새로운 레시피 등록 - POST 메서드 추가
    @POST("/recipes")
    fun createRecipe(@Body request: RecipeCreateRequest): Call<RecipeCreateResponse>

    @Multipart
    @POST("/recipes/upload")
    fun addRecipeWithImage(
        @Part("name") name: RequestBody,
        @Part("summary") summary: RequestBody,
        @Part("ingredients") ingredients: RequestBody,
        @Part("instructions") instructions: RequestBody,
        @Part("timetaken") timetaken: RequestBody,
        @Part("difficultylevel") difficultylevel: RequestBody,
        @Part("allergies") allergies: RequestBody,
        @Part("disease") disease: RequestBody,
        @Part("diseasereason") diseasereason: RequestBody?,
        @Part("category") category: RequestBody,
        @Part image: MultipartBody.Part? // nullable, 없어도 multipart 유지
    ): Call<RecipeCreateResponse>



}