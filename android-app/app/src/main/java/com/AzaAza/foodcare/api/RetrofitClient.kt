package com.AzaAza.foodcare.api

import UserApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {
    private const val BASE_URL = "https://foodcare-69ae76eec1bf.herokuapp.com/"

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val ingredientApiService: IngredientApiService by lazy {
        instance.create(IngredientApiService::class.java)
    }

    val recipeApiService: RecipeApiService by lazy {
        instance.create(RecipeApiService::class.java)
    }
    val userApiService: UserApiService by lazy {
        instance.create(UserApiService::class.java)
    }
    // 가계부 API 서비스 추가
    val expenseApiService: ExpenseApiService by lazy {
        instance.create(ExpenseApiService::class.java)
    }
    val userInfoApiService: UserInfoApiService by lazy {
        instance.create(UserInfoApiService::class.java)
    }
}
