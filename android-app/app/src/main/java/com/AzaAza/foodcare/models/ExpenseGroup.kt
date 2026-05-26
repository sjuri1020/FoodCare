package com.AzaAza.foodcare.models

import android.content.Context
import android.util.Log

// 날짜별 그룹화된 지출 내역 (공유 모드 지원)
data class ExpenseGroup(
    val date: String,         // 날짜 (yyyy-MM-dd)
    val displayTitle: String, // 표시 제목 ("오늘", "어제", 또는 날짜)
    val expenses: MutableList<ExpenseDto> = mutableListOf(),
    val sharedExpenses: MutableList<SharedExpenseDto> = mutableListOf(), // 공유 모드용
    var isExpanded: Boolean = false,
    val isSharedMode: Boolean = false // 공유 모드 여부
) {
    // currentUserId를 별도 프로퍼티로 관리 (data class 매개변수에서 제외하여 충돌 방지)
    var currentUserId: Int = -1
        private set

    // 사용자 ID -> 이름 매핑 정보
    private var userIdToNameMap: Map<Int, String> = emptyMap()

    // 그룹의 총 금액 계산 (개인/공유 모드 구분)
    fun getTotalAmount(): Double {
        return if (isSharedMode) {
            sharedExpenses.sumOf { it.amount }
        } else {
            expenses.sumOf { it.amount }
        }
    }

    // 전체 지출 개수 반환
    fun getExpenseCount(): Int {
        return if (isSharedMode) {
            sharedExpenses.size
        } else {
            expenses.size
        }
    }

    // 개별 지출 항목을 위한 데이터 클래스
    data class ExpenseItem(
        val id: Int,
        val productName: String,
        val amount: Double,
        val dateTime: String,
        val memo: String?,
        val ownerName: String,
        val userId: Int,
        val isSharedMode: Boolean
    )

    // 현재 사용자 ID 설정 메서드 (충돌 방지를 위해 setCurrentUserId 대신 사용)
    fun updateCurrentUserId(userId: Int) {
        this.currentUserId = userId
        Log.d("ExpenseGroup", "currentUserId 설정됨: $userId")
    }

    // 사용자 매핑 정보 설정
    fun setUserMapping(mapping: Map<Int, String>) {
        this.userIdToNameMap = mapping.toMap() // 복사본 생성
        Log.d("ExpenseGroup", "사용자 매핑 설정됨: $userIdToNameMap")
    }

    // 사용자 ID로 이름 조회
    private fun getUserNameById(userId: Int): String {
        return when {
            // 현재 사용자인 경우
            currentUserId != -1 && userId == currentUserId -> {
                Log.d("ExpenseGroup", "현재 사용자 감지: userId=$userId -> '나'")
                "나"
            }
            // 매핑에서 이름 찾기
            userIdToNameMap.containsKey(userId) -> {
                val name = userIdToNameMap[userId]!!
                Log.d("ExpenseGroup", "매핑에서 이름 찾음: userId=$userId -> '$name'")
                name
            }
            // 매핑에서 찾을 수 없는 경우
            else -> {
                Log.w("ExpenseGroup", "사용자 이름을 찾을 수 없음: userId=$userId, currentUserId=$currentUserId, mapping=$userIdToNameMap")
                "구성원"
            }
        }
    }

    // 통합된 지출 항목 리스트 반환 (어댑터에서 사용)
    fun getExpenseItems(): List<ExpenseItem> {
        return if (isSharedMode) {
            sharedExpenses.map { expense ->
                Log.d("ExpenseGroup", "=== SharedExpense 처리 시작 ===")
                Log.d("ExpenseGroup", "Product: ${expense.productName}")
                Log.d("ExpenseGroup", "서버 ownerName: '${expense.ownerName}'")
                Log.d("ExpenseGroup", "expense.userId: ${expense.userId}")
                Log.d("ExpenseGroup", "currentUserId: $currentUserId")
                Log.d("ExpenseGroup", "userMapping: $userIdToNameMap")

                // 실제 표시할 이름 결정 (서버 ownerName 무시하고 매핑 사용)
                val displayOwnerName = getUserNameById(expense.userId)

                Log.d("ExpenseGroup", "최종 displayOwnerName: '$displayOwnerName'")
                Log.d("ExpenseGroup", "=== SharedExpense 처리 완료 ===")

                ExpenseItem(
                    id = expense.id,
                    productName = expense.productName,
                    amount = expense.amount,
                    dateTime = expense.dateTime,
                    memo = expense.memo,
                    ownerName = displayOwnerName,
                    userId = expense.userId,
                    isSharedMode = true
                )
            }
        } else {
            expenses.map { expense ->
                ExpenseItem(
                    id = expense.id,
                    productName = expense.productName,
                    amount = expense.amount,
                    dateTime = expense.dateTime,
                    memo = expense.memo,
                    ownerName = "나",
                    userId = expense.userId,
                    isSharedMode = false
                )
            }
        }
    }
}