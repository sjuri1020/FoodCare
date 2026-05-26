package com.AzaAza.foodcare.ui

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.data.UserSession
import kotlinx.coroutines.*

class MoreIngredientsDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "MoreIngredientsDialog"
        private const val ARG_TARGET_ID = "target_id"
        private const val ARG_IS_SHARED = "is_shared"

        fun newInstance(targetId: Int, isSharedMode: Boolean): MoreIngredientsDialogFragment {
            val fragment = MoreIngredientsDialogFragment()
            val args = Bundle()
            args.putInt(ARG_TARGET_ID, targetId)
            args.putBoolean(ARG_IS_SHARED, isSharedMode)
            fragment.arguments = args
            return fragment
        }
    }

    private var targetId: Int = -1
    private var isSharedMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 인자에서 설정 가져오기
        targetId = arguments?.getInt(ARG_TARGET_ID, -1) ?: -1
        isSharedMode = arguments?.getBoolean(ARG_IS_SHARED, false) ?: false

        if (targetId == -1) {
            Toast.makeText(requireContext(), "잘못된 접근입니다", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        Log.d(TAG, "다이얼로그 초기화 - targetId: $targetId, isSharedMode: $isSharedMode")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_more_ingredients)

        val container = dialog.findViewById<LinearLayout>(R.id.moreIngredientsLayout)
        val inflater = LayoutInflater.from(context)
        val closeButton = dialog.findViewById<Button>(R.id.closeButton)

        // 제목 설정 - dialogTitle ID가 레이아웃에 없으므로 주석 처리
        // 필요시 레이아웃 파일에 TextView를 추가하고 ID를 dialogTitle로 설정하세요
        /*
        val titleText = dialog.findViewById<TextView>(R.id.dialogTitle)
        titleText?.text = if (isSharedMode) {
            "가족 구매 내역 더보기"
        } else {
            "구매 내역 더보기"
        }
        */

        closeButton?.setOnClickListener {
            dismiss()
        }

        // 데이터 로드
        loadIngredients(container, inflater)

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    /**
     * 재료 데이터 로드 (공유/개인 모드 구분)
     */
    private fun loadIngredients(container: LinearLayout, inflater: LayoutInflater) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== 더보기 재료 로드 시작 ===")
                Log.d(TAG, "모드: ${if (isSharedMode) "공유" else "개인"}")
                Log.d(TAG, "targetId: $targetId")

                val top10: List<Pair<String, Int>> = if (isSharedMode) {
                    Log.d(TAG, "공유 모드 - 그룹 전체 장보기 데이터 조회")
                    val sharedExpenses = RetrofitClient.expenseApiService.getSharedExpenses(targetId)

                    Log.d(TAG, "전체 공유 지출: ${sharedExpenses.size}개")

                    val groceryExpenses = sharedExpenses.filter { expense ->
                        val isGrocery = expense.categoryName?.trim()?.equals("장보기", ignoreCase = true) == true
                        if (isGrocery) {
                            Log.d(TAG, "장보기 지출: ${expense.productName} by userId ${expense.userId}")
                        }
                        isGrocery
                    }

                    Log.d(TAG, "장보기 카테고리 지출: ${groceryExpenses.size}개")

                    groceryExpenses
                        .groupingBy { expense -> expense.productName.trim() }
                        .eachCount()
                        .toList()
                        .sortedByDescending { it.second }
                        .take(10)
                        .also { top10List ->
                            Log.d(TAG, "공유 모드 상위 10개 재료:")
                            top10List.forEachIndexed { index, (name, count) ->
                                Log.d(TAG, "  ${index + 1}. $name: ${count}회")
                            }
                        }
                } else {
                    Log.d(TAG, "개인 모드 - 개인 장보기 데이터 조회")
                    val personalExpenses = RetrofitClient.expenseApiService.getExpenses(targetId)

                    Log.d(TAG, "전체 개인 지출: ${personalExpenses.size}개")

                    val groceryExpenses = personalExpenses.filter { expense ->
                        val isGrocery = expense.categoryName?.trim()?.equals("장보기", ignoreCase = true) == true
                        if (isGrocery) {
                            Log.d(TAG, "장보기 지출: ${expense.productName}")
                        }
                        isGrocery
                    }

                    Log.d(TAG, "장보기 카테고리 지출: ${groceryExpenses.size}개")

                    groceryExpenses
                        .groupingBy { expense -> expense.productName.trim() }
                        .eachCount()
                        .toList()
                        .sortedByDescending { it.second }
                        .take(10)
                        .also { top10List ->
                            Log.d(TAG, "개인 모드 상위 10개 재료:")
                            top10List.forEachIndexed { index, (name, count) ->
                                Log.d(TAG, "  ${index + 1}. $name: ${count}회")
                            }
                        }
                }

                // 6위부터 10위까지 표시
                val items6to10 = top10.drop(5)

                withContext(Dispatchers.Main) {
                    container.removeAllViews()

                    if (items6to10.isEmpty()) {
                        val noDataTextView = TextView(requireContext()).apply {
                            text = if (isSharedMode) {
                                "추가로 표시할 가족 장보기 내역이 없습니다."
                            } else {
                                "추가로 표시할 장보기 내역이 없습니다."
                            }
                            textSize = 14f
                            setPadding(24, 28, 24, 28)
                            gravity = Gravity.CENTER
                        }
                        container.addView(noDataTextView)
                    } else {
                        items6to10.forEachIndexed { index, (name, count) ->
                            val cardView = inflater.inflate(R.layout.item_ingredient_card, container, false)

                            cardView.findViewById<TextView>(R.id.rankCircle).text = "${index + 6}"
                            cardView.findViewById<TextView>(R.id.ingredientName).text = name

                            val frequencyText = "월 ${count}회 구매"
                            cardView.findViewById<TextView>(R.id.frequencyText).text = frequencyText

                            cardView.findViewById<Button>(R.id.addButton).setOnClickListener {
                                (activity as? ExpenseAnalysisActivity)?.addToShoppingList(name, count)
                            }

                            container.addView(cardView)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "재료 로드 실패", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "데이터 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}