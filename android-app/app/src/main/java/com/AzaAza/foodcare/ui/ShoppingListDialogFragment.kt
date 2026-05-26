package com.AzaAza.foodcare.ui

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.AzaAza.foodcare.R

class ShoppingListDialogFragment(
    private val addedItems: MutableList<Triple<String, Int, Int>>,
    private val onItemsUpdated: () -> Unit = {}
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_shopping_list)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.setCanceledOnTouchOutside(false)

        val closeBtn = dialog.findViewById<ImageView>(R.id.closeButton)
        val saveLayout = dialog.findViewById<LinearLayout>(R.id.saveButtonLayout)
        val saveBtn = dialog.findViewById<TextView>(R.id.saveButton)
        val itemCountText = dialog.findViewById<TextView>(R.id.itemCountText)
        val listLayout = dialog.findViewById<LinearLayout>(R.id.shoppingListLayout)
        val emptyLayout = dialog.findViewById<LinearLayout>(R.id.emptyLayout)

        val quantityMap = mutableMapOf<String, Int>()  // name -> quantity

        if (addedItems.isEmpty()) {
            itemCountText.text = "현재 0개 항목이 있습니다"
            emptyLayout.visibility = View.VISIBLE
            listLayout.visibility = View.GONE
            saveLayout.visibility = View.GONE
        } else {
            itemCountText.text = "현재 ${addedItems.size}개 항목이 있습니다"
            emptyLayout.visibility = View.GONE
            listLayout.visibility = View.VISIBLE
            saveLayout.visibility = View.VISIBLE

            val inflater = LayoutInflater.from(requireContext())
            val itemsCopy = addedItems.toList()

            for ((name, count, quantity) in itemsCopy) {
                val itemView = inflater.inflate(R.layout.item_shopping_list, listLayout, false)

                val nameText = itemView.findViewById<TextView>(R.id.itemName)
                val freqText = itemView.findViewById<TextView>(R.id.itemFreq)
                val quantityText = itemView.findViewById<TextView>(R.id.quantityText)
                val minusBtn = itemView.findViewById<TextView>(R.id.minusButton)
                val plusBtn = itemView.findViewById<TextView>(R.id.plusButton)
                val deleteBtn = itemView.findViewById<ImageButton>(R.id.deleteButton)

                nameText.text = name
                freqText.text = "월 ${count}회 구매"
                quantityText.text = quantity.toString()
                quantityMap[name] = quantity

                minusBtn.setOnClickListener {
                    val current = quantityMap[name] ?: 1
                    if (current > 1) {
                        quantityMap[name] = current - 1
                        quantityText.text = (current - 1).toString()
                    }
                }

                plusBtn.setOnClickListener {
                    val current = quantityMap[name] ?: 1
                    quantityMap[name] = current + 1
                    quantityText.text = (current + 1).toString()
                }

                deleteBtn.setOnClickListener {
                    listLayout.removeView(itemView)
                    /*와이럼*/
                    addedItems.removeIf { it.first == name }
                    quantityMap.remove(name)

                    val remaining = listLayout.childCount
                    itemCountText.text = "현재 ${remaining}개 항목이 있습니다"

                    if (remaining == 0) {
                        emptyLayout.visibility = View.VISIBLE
                        listLayout.visibility = View.GONE
                        saveLayout.visibility = View.GONE
                    }

                    onItemsUpdated()
                }

                listLayout.addView(itemView)
            }
        }

        // 저장 버튼 클릭 시 수량을 반영하여 업데이트
        saveBtn.setOnClickListener {
            for (i in addedItems.indices) {
                val (name, count, _) = addedItems[i]
                val updatedQty = quantityMap[name] ?: 1
                addedItems[i] = Triple(name, count, updatedQty)
            }
            onItemsUpdated()
            dismiss()
        }

        closeBtn.setOnClickListener {
            onItemsUpdated()
            dismiss()
        }

        return dialog
    }
}
