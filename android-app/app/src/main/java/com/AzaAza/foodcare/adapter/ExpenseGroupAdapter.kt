package com.AzaAza.foodcare.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.models.ExpenseDto
import com.AzaAza.foodcare.models.SharedExpenseDto
import com.AzaAza.foodcare.models.ExpenseGroup
import java.text.NumberFormat
import java.util.*

class ExpenseGroupAdapter(
    private val expenseGroups: MutableList<ExpenseGroup>,
    private val onItemLongClick: (ExpenseGroup.ExpenseItem) -> Unit
) : RecyclerView.Adapter<ExpenseGroupAdapter.ItemViewHolder>() {

    companion object {
        private const val TAG = "ExpenseGroupAdapter"
    }

    // 총합 업데이트 콜백 (액티비티 쪽에서 연결해줌)
    private var onTotalChangedListener: ((Float) -> Unit)? = null

    fun setOnTotalChangedListener(listener: (Double) -> Unit) {
        this.onTotalChangedListener = { amount -> listener(amount.toDouble()) }
    }

    override fun getItemCount(): Int {
        // 확장된 그룹들만 아이템 개수로 반영
        val count = expenseGroups.sumOf { group ->
            if (group.isExpanded) group.getExpenseCount() else 0
        }
        Log.d(TAG, "getItemCount: $count")
        return count
    }

    // position 기준으로 실제 ExpenseItem 찾아서 반환
    private fun getExpenseItemAtPosition(position: Int): ExpenseGroup.ExpenseItem? {
        var currentPosition = 0
        for (group in expenseGroups) {
            if (group.isExpanded) {
                val groupSize = group.getExpenseCount()
                if (position < currentPosition + groupSize) {
                    val itemIndex = position - currentPosition
                    val expenseItems = group.getExpenseItems()
                    return if (itemIndex < expenseItems.size) {
                        val item = expenseItems[itemIndex]
                        Log.d(TAG, "getExpenseItemAtPosition($position): ${item.productName}, 작성자: ${item.ownerName}")
                        item
                    } else {
                        Log.w(TAG, "itemIndex 초과: $itemIndex >= ${expenseItems.size}")
                        null
                    }
                }
                currentPosition += groupSize
            }
        }
        Log.w(TAG, "getExpenseItemAtPosition($position): 해당 항목 없음")
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.expense_group_item, parent, false)
        Log.d(TAG, "ViewHolder 생성됨")
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val expenseItem = getExpenseItemAtPosition(position)
        if (expenseItem != null) {
            holder.bind(expenseItem)

            // 롱클릭 시 콜백 실행
            holder.itemView.setOnLongClickListener {
                onItemLongClick(expenseItem)
                true
            }

            // 기본 배경 설정
            holder.itemView.setBackgroundResource(R.drawable.expense_item_normal_bg)

            // 너비 MATCH_PARENT로 맞추기
            val layoutParams = holder.itemView.layoutParams
            layoutParams.width = LayoutParams.MATCH_PARENT

            // 마진 제거 (간격 없애기 위해)
            if (layoutParams is MarginLayoutParams) {
                layoutParams.setMargins(0, 0, 0, 0)
            }

            holder.itemView.layoutParams = layoutParams
        } else {
            Log.e(TAG, "onBindViewHolder: item이 null임 (position=$position)")
        }
    }

    // 아이템 삭제 처리 (ID 기준으로 삭제)
    fun removeExpense(expenseId: Int): Boolean {
        Log.d(TAG, "removeExpense 호출: id=$expenseId")

        for (groupIndex in expenseGroups.indices) {
            val group = expenseGroups[groupIndex]

            if (group.isSharedMode) {
                // 공유 모드일 경우
                val itemIndex = group.sharedExpenses.indexOfFirst { it.id == expenseId }
                if (itemIndex != -1) {
                    group.sharedExpenses.removeAt(itemIndex)
                    handleGroupRemoval(groupIndex, group)
                    return true
                }
            } else {
                // 개인 모드일 경우
                val itemIndex = group.expenses.indexOfFirst { it.id == expenseId }
                if (itemIndex != -1) {
                    group.expenses.removeAt(itemIndex)
                    handleGroupRemoval(groupIndex, group)
                    return true
                }
            }
        }

        Log.w(TAG, "삭제 대상 없음: id=$expenseId")
        return false
    }

    // 해당 그룹이 비었으면 리스트에서 제거
    private fun handleGroupRemoval(groupIndex: Int, group: ExpenseGroup) {
        if (group.getExpenseCount() == 0) {
            Log.d(TAG, "빈 그룹 제거됨: ${group.date}")
            expenseGroups.removeAt(groupIndex)
        }
        notifyDataSetChanged()
    }

    // 개별 항목에 대한 ViewHolder
    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.itemNameText)
        private val dateText: TextView = itemView.findViewById(R.id.itemDateText)
        private val amountText: TextView = itemView.findViewById(R.id.itemAmountText)
        private val categoryIndicator: View = itemView.findViewById(R.id.categoryIndicator)

        fun bind(expenseItem: ExpenseGroup.ExpenseItem) {
            // 작성자 이름 체크 (공유 모드일 경우만)
            val displayName = if (expenseItem.isSharedMode) {
                when {
                    expenseItem.ownerName.isNullOrBlank() || expenseItem.ownerName.equals("null", true) -> {
                        Log.w(TAG, "owner 이름 없음: id=${expenseItem.id}")
                        "알 수 없음"
                    }
                    else -> expenseItem.ownerName
                }.let { "${expenseItem.productName} ($it)" }
            } else {
                expenseItem.productName
            }

            nameText.text = displayName

            // 날짜 포맷 yyyy-MM-dd 만 뽑아냄
            val dateOnly = try {
                expenseItem.dateTime.split(" ")[0]
            } catch (e: Exception) {
                Log.w(TAG, "날짜 파싱 실패: ${expenseItem.dateTime}", e)
                expenseItem.dateTime
            }
            dateText.text = dateOnly

            // 금액 표시: 한국식 세 자리 단위
            val formatter = NumberFormat.getInstance(Locale.KOREA)
            amountText.text = "${formatter.format(expenseItem.amount.toInt())}원"

            // 카테고리 인디케이터 색상 (초록 고정)
            categoryIndicator.setBackgroundColor(android.graphics.Color.parseColor("#00E676"))

            val memoText: TextView = itemView.findViewById(R.id.itemMemoText)
            if (!expenseItem.memo.isNullOrBlank()) {
                memoText.text = expenseItem.memo
                memoText.visibility = View.VISIBLE
            } else {
                memoText.visibility = View.GONE
            }

            Log.d(TAG, "bind 완료: ${nameText.text}, ${dateText.text}, ${amountText.text}")
        }
    }
}
