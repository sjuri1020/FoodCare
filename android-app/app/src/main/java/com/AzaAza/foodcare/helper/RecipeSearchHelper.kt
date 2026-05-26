package com.AzaAza.foodcare.helper

import com.AzaAza.foodcare.models.Recipe
import java.text.Normalizer

object RecipeSearchHelper {
    private val CHOSUNG = arrayOf(
        'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    )

    fun filter(query: String, list: List<Recipe>): List<Recipe> {
        val normQuery = normalize(query)
        val queryChosung = getChosung(query)

        return list.filter {
            val nameNorm = normalize(it.name)
            val nameChosung = getChosung(it.name)
            nameNorm.contains(normQuery) || nameChosung.contains(queryChosung)
        }
    }


    private fun normalize(str: String): String {
        return Normalizer.normalize(str.lowercase().replace(" ", ""), Normalizer.Form.NFKD)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
    }

    private fun getChosung(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            if (c in '가'..'힣') {
                val uniVal = c - '가'
                val cho = uniVal / (21 * 28)
                sb.append(CHOSUNG[cho])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}