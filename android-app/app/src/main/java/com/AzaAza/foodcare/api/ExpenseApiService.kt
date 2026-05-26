package com.AzaAza.foodcare.api

import com.AzaAza.foodcare.models.CategoryDto
import com.AzaAza.foodcare.models.ExpenseDto
import com.AzaAza.foodcare.models.SharedExpenseDto
import com.AzaAza.foodcare.models.MemberResponse
import com.AzaAza.foodcare.models.MonthlySummaryResponse
import com.AzaAza.foodcare.models.SharedMonthlySummaryResponse
import retrofit2.Response
import retrofit2.http.*

interface ExpenseApiService {
    // =================== 개인 모드 API ===================
    @GET("expense_categories")
    suspend fun getCategories(@Query("user_id") userId: Int): List<CategoryDto>

    @GET("expenses")
    suspend fun getExpenses(@Query("user_id") userId: Int): List<ExpenseDto>

    @POST("expenses")
    suspend fun addExpense(@Body expense: ExpenseDto): Response<ExpenseDto>

    @DELETE("expenses/{expense_id}")
    suspend fun deleteExpense(@Path("expense_id") expenseId: Int, @Query("user_id") userId: Int): Response<Void>

    @GET("expenses/category/{category_id}")
    suspend fun getExpensesByCategory(
        @Path("category_id") categoryId: Int,
        @Query("user_id") userId: Int
    ): List<ExpenseDto>

    @GET("expenses/summary/monthly")
    suspend fun getMonthlySummary(
        @Query("user_id") userId: Int,
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null
    ): MonthlySummaryResponse

    // =================== 공유 모드 API ===================

    @GET("expense_categories/shared/{owner_id}")
    suspend fun getSharedCategories(@Path("owner_id") ownerId: Int): List<CategoryDto>

    // 공유 지출 조회 - 권한 확인을 위해 현재 사용자 ID 추가
    @GET("expenses/shared/{owner_id}")
    suspend fun getSharedExpenses(
        @Path("owner_id") ownerId: Int,
        @Query("user_id") userId: Int? = null  // 현재 사용자 ID 추가 (선택적)
    ): List<SharedExpenseDto>

    @GET("expenses/summary/monthly/shared/{owner_id}")
    suspend fun getSharedMonthlySummary(
        @Path("owner_id") ownerId: Int,
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("user_id") userId: Int? = null  // 권한 확인용 사용자 ID 추가
    ): SharedMonthlySummaryResponse

    @GET("expenses/category/shared/{owner_id}/{category_id}")
    suspend fun getSharedExpensesByCategory(
        @Path("owner_id") ownerId: Int,
        @Path("category_id") categoryId: Int,
        @Query("user_id") userId: Int? = null  // 권한 확인용 사용자 ID 추가
    ): List<SharedExpenseDto>

    // =================== 멤버 관리 API ===================

    @GET("members/{owner_id}")
    suspend fun getMembers(@Path("owner_id") ownerId: Int): List<MemberResponse>
}