package com.AzaAza.foodcare.data
/* 민은 이 클래스 쓰는 거 맞니?*/

object ShoppingList {
    // 식재료명 → 보유 개수
    val items = mutableMapOf<String, Int>()

    /** 개수 +1 후 반환 */
    fun addItem(itemName: String): Int {
        val cnt = (items[itemName] ?: 0) + 1
        items[itemName] = cnt
        return cnt
    }

    /**
     * 개수 -1.
     * 감소 후 남은 개수를 반환하거나, 0이 되어 삭제된 경우 null 반환
     */
    fun decreaseItem(itemName: String): Int? {
        val current = items[itemName] ?: return null
        return if (current > 1) {
            val newCnt = current - 1
            items[itemName] = newCnt
            newCnt
        } else {
            // 1이던 항목은 제거
            items.remove(itemName)
            null
        }
    }
}
