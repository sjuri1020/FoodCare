package com.AzaAza.foodcare.models

import com.AzaAza.foodcare.R
import com.google.gson.annotations.SerializedName

data class RecipeDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("summary") val summary: String?,
    @SerializedName("ingredients") val ingredients: String,  // DB에는 텍스트로 저장되어 있음
    @SerializedName("instructions") val instructions: String,
    @SerializedName("timetaken") val timetaken: String,
    @SerializedName("difficultylevel") val difficultylevel: String,
    @SerializedName("allergies") val allergies: String?,
    @SerializedName("disease") val disease: String?,
    @SerializedName("diseasereason") val diseasereason: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("image_url") val imageUrl: String?
) {
    // RecipeDto를 Recipe 객체로 변환하는 함수
    fun toRecipe(userIngredients: List<String>): Recipe {
        val ingredientsList = ingredients.split(",").map { it.trim() }

        // 개선된 재료 매칭 로직
        val matched = findMatchedIngredients(ingredientsList, userIngredients)

        // 음식 이름에 따라 이미지 선택
        val imageRes = when (name) {
            "김치찌개" -> R.drawable.dish_img_kimchi_stew
            "김치볶음밥" -> R.drawable.dish_img_kimchi_bokkeumbap
            "콩국수" -> R.drawable.dish_img_bean_noodles
            "비빔밥" -> R.drawable.dish_img_bibimbap
            "부대찌개" -> R.drawable.dish_img_budaejjigae
            "청국장" -> R.drawable.dish_img_cheonggukjang
            "잡채" -> R.drawable.dish_img_japchae
            "카레" -> R.drawable.dish_img_curry
            "갈비찜" -> R.drawable.dish_img_galbijjim
            "감자볶음" -> R.drawable.dish_img_gamjabokkeum
            "감자전" -> R.drawable.dish_img_gamjajeon
            "김치라면" -> R.drawable.dish_img_kimchiramen
            "김치전" -> R.drawable.dish_img_kimchijeon
            "닭갈비" -> R.drawable.dish_img_dakgalbi
            "된장찌개" -> R.drawable.dish_img_doenjangjjigae
            "떡볶이" -> R.drawable.dish_img_tteokbokki
            "미역국" -> R.drawable.dish_img_miyeokguk
            "소불고기" -> R.drawable.dish_img_sobulgogi
            "소시지볶음" -> R.drawable.dish_img_sosisibokkeum
            "순두부찌개" -> R.drawable.dish_img_sundubujjigae
            "오이무침" -> R.drawable.dish_img_oimumchim
            "오트밀미역죽" -> R.drawable.dish_img_oatmealmiyukjuk
            "유부초밥" -> R.drawable.dish_img_yubuchobap
            "잔치국수" -> R.drawable.dish_img_janchiguksu
            "콩나물국" -> R.drawable.dish_img_kongnamulguk
            "토스트" -> R.drawable.dish_img_toast
            "호박죽" -> R.drawable.dish_img_hobakjuk
            "육개장" -> R.drawable.dish_img_yukgaejang
            "샤브샤브" -> R.drawable.dish_img_shabushabu
            "닭볶음탕" -> R.drawable.dish_img_chicken_bokkeumtang
            "대구탕" -> R.drawable.dish_img_daegutang
            "감자탕" -> R.drawable.dish_img_gamjatang
            "고르곤졸라 피자" -> R.drawable.dish_img_gorgonzola_pizza
            "궁중떡볶이" -> R.drawable.dish_img_gungjung_tteokbokki
            "함박스테이크" -> R.drawable.dish_img_hamburger_steak
            "마라탕" -> R.drawable.dish_img_malatang
            "마파두부" -> R.drawable.dish_img_mapa_tofu
            "쌀국수" -> R.drawable.dish_img_pad_thai
            "봉골레파스타" -> R.drawable.dish_img_vongole_pasta
            "포" -> R.drawable.dish_img_pho
            "나시 고랭" -> R.drawable.dish_img_nasi_goreng
            "수정과" -> R.drawable.dish_img_sujenggua
            "식혜" -> R.drawable.dish_img_sikhae
            "약과" -> R.drawable.dish_img_yakgua
            "도토리묵" -> R.drawable.dish_img_acorn_jello
            "장어구이" -> R.drawable.dish_img_broiled_eels
            "관자버터구이" -> R.drawable.dish_img_grilled_tube_butter
            "마라샹궈" -> R.drawable.dish_img_malaxianguo
            "오코노미야끼" -> R.drawable.dish_img_okonomiyakki
            "고추잡채" -> R.drawable.dish_img_red_pepper_japchae
            "곱도리탕" -> R.drawable.dish_img_gopdoritang
            "동그랑땡" -> R.drawable.dish_img_donggeurangddang
            "부추전" -> R.drawable.dish_img_buchujeon
            "양장피" -> R.drawable.dish_img_sheepskin
            "오리탕" -> R.drawable.dish_img_duck_soup
            "LA 양념갈비" -> R.drawable.dish_img_la_ribs
            "감바스" -> R.drawable.dish_img_gambas
            "계란말이" -> R.drawable.dish_img_egg_rolled
            "달걀볶음밥" -> R.drawable.dish_img_egg_fried_rice
            "고등어구이" -> R.drawable.dish_img_grilled_mackerel
            "꽃게탕" -> R.drawable.dish_img_crab_soup
            "돈까스" -> R.drawable.dish_img_pork_cutlet
            "동태탕" -> R.drawable.dish_img_pollack_soup
            "로제파스타" -> R.drawable.dish_img_rose_pasta
            "보쌈" -> R.drawable.dish_img_bossam
            "브라우니" -> R.drawable.dish_img_brownie
            "비빔국수" -> R.drawable.dish_img_bibimbap
            "소고기무국" -> R.drawable.dish_img_beef_radish_soup
            "수육" -> R.drawable.dish_img_boiled_pork
            "순대볶음" -> R.drawable.dish_img_soondae
            "스테이크" -> R.drawable.dish_img_steak
            "알탕" -> R.drawable.dish_img_fish_roe_soup
            "연포탕" -> R.drawable.dish_img_yeonpo_soup
            "오므라이스" -> R.drawable.dish_img_omelet_rice
            "조개탕" -> R.drawable.dish_img_clam_soup
            "족발" -> R.drawable.dish_img_pig_hocks
            "짜장면" -> R.drawable.dish_img_black_bean_sauce_noodles
            "짬뽕" -> R.drawable.dish_img_jjambbong
            "초밥" -> R.drawable.dish_img_sushi
            "초코칩 쿠키" -> R.drawable.dish_img_chocolate_chip_cookies
            "칼국수" -> R.drawable.dish_img_kalguksu
            "케이크" -> R.drawable.dish_img_cake
            "코다리찜" -> R.drawable.dish_img_kodarijjim
            "콩나물 불고기" -> R.drawable.dish_img_bean_sprout_bulgogi
            "투움바 파스타" -> R.drawable.dish_img_toowoomba_pasta
            "피넛버터 쿠키" -> R.drawable.dish_img_peanut_butter_cookies
            "제육볶음" -> R.drawable.dish_img_jeyuk_bokkeum
            "마제소바" -> R.drawable.dish_img_mazesoba
            "시래기국" -> R.drawable.dish_img_siraegikuk
            "크림소스 뇨끼" -> R.drawable.dish_img_cream_gnocchi
            "똠양꿍" -> R.drawable.dish_img_tomyumgoong
            "카레 프라이드 누들" -> R.drawable.dish_img_curry_fried_noodle
            "팟타이" -> R.drawable.dish_img_pad_thai
            "탄두리 치킨" -> R.drawable.dish_img_tandoori_chicken
            "고이꾸온" -> R.drawable.dish_img_goicuon
            "치킨 라이스" -> R.drawable.dish_img_chicken_rice
            "아도보" -> R.drawable.dish_img_adobo
            "팔보채" -> R.drawable.dish_img_eight_bodied_vegetables
            "깐풍기" -> R.drawable.dish_img_deep_fried_chickeninhot_pepper_sauce
            "비리야니" -> R.drawable.dish_img_biryani
            "까르보나라" -> R.drawable.dish_img_carbonara
            "치킨 커리" -> R.drawable.dish_img_chicken_curry
            "동파육" -> R.drawable.dish_img_dongpo_pork
            "깐쇼새우" -> R.drawable.dish_img_gansho_shrimp
            "망고 스티키 라이스" -> R.drawable.dish_img_mango_sticky_rice
            "멘보샤" -> R.drawable.dish_img_menbosha
            "나시 르막" -> R.drawable.dish_img_menbosha
            "매생이국" -> R.drawable.dish_img_maesaengi_guk
            "팔락 파니르" -> R.drawable.dish_img_palak_paneer
            "사테" -> R.drawable.dish_img_satay
            "탕수육" -> R.drawable.dish_img_tangsuyuk
            "똠양누들" -> R.drawable.dish_img_tomyam_noodle
            "샤오롱바오" -> R.drawable.dish_img_xiaolongbao
            "야끼소바" -> R.drawable.dish_img_yakisoba
            "엿" -> R.drawable.dish_img_yeot
            "유린기" -> R.drawable.dish_img_yurinji
            "짜사이무침" -> R.drawable.dish_img_zhacai_muchim
            "건두부볶음" -> R.drawable.dish_img_stir_fried_dried_bean_curd
            "꿔바로우" -> R.drawable.dish_img_guobaorou
            "규동" -> R.drawable.dish_img_gyudong
            "가라아게" -> R.drawable.dish_img_karaage
            "가츠산도" -> R.drawable.dish_img_katsu_sandwich
            "양꼬치" -> R.drawable.dish_img_lamb_skewers
            "나가사키 짬뽕" -> R.drawable.dish_img_nagasaki_jjamppong
            "리코타 샐러드" -> R.drawable.dish_img_ricotta_salad
            "스팸무스비" -> R.drawable.dish_img_spam_musubi
            "스키야키" -> R.drawable.dish_img_sukiyaki
            "야키토리" -> R.drawable.dish_img_yakitori
            "마라두부" -> R.drawable.dish_img_mala_tofu
            "가츠동" -> R.drawable.dish_img_katsudon
            "타코야끼" -> R.drawable.dish_img_takoyaki
            "소바" -> R.drawable.dish_img_soba
            "우동" -> R.drawable.dish_img_udon
            "니쿠자가" -> R.drawable.dish_img_nikujaga
            "명란오차즈케" -> R.drawable.dish_img_mentaiko_ochazuke
            "연어 스테이크" -> R.drawable.dish_img_salmon_steak
            "크림 머쉬룸 스프" -> R.drawable.dish_img_cream_mushroom_soup
            "마카로니 앤 치즈" -> R.drawable.dish_img_macaroni_and_cheese
            "치즈 버거" -> R.drawable.dish_img_cheese_burger
            "라따뚜이" -> R.drawable.dish_img_ratatouille
            "파에야" -> R.drawable.dish_img_paella
            "프리타타" -> R.drawable.dish_img_pretata
            "로스트 치킨" -> R.drawable.dish_img_roasted_chicken
            "호떡" -> R.drawable.dish_img_hotteok
            "팥빙수" -> R.drawable.dish_img_patbingsu
            "떡" -> R.drawable.dish_img_rice_cake
            "치즈케이크" -> R.drawable.dish_img_cheesecake
            "붕어빵" -> R.drawable.dish_img_fish_shaped_bun
            "파운드케이크" -> R.drawable.dish_img_poundcake
            "마카롱" -> R.drawable.dish_img_macaron
            "과일 타르트" -> R.drawable.dish_img_fruit_tart
            "인절미" -> R.drawable.dish_img_injeolmi
            "카스테라" -> R.drawable.dish_img_castella
            "호두파이" -> R.drawable.dish_img_walnutpie
            "푸딩" -> R.drawable.dish_img_pudding

            else -> R.drawable.no_img  // 기본 이미지
        }

        return Recipe(
            name = name,
            summary = summary,
            description = instructions.take(50) + if (instructions.length > 50) "..." else "",
            instructions = instructions,          // 전체 순서 저장
            imageResId = imageRes,
            imageUrl = imageUrl,
            ingredients = ingredientsList,
            matchedCount = matched.size,
            matchedIngredients = matched,
            timeTaken = timetaken,
            difficulty = difficultylevel,
            allergies = allergies,
            disease = disease,
            diseaseReason = diseasereason,
            category = category
        )
    }

    /**
     * 개선된 재료 매칭 로직
     * 괄호 안의 대체 재료도 고려하여 매칭
     */
    private fun findMatchedIngredients(recipeIngredients: List<String>, userIngredients: List<String>): List<String> {
        val matched = mutableListOf<String>()

        for (recipeIngredient in recipeIngredients) {
            val cleanIngredient = recipeIngredient.trim()

            // 괄호가 있는 경우와 없는 경우 모두 처리
            if (isIngredientMatched(cleanIngredient, userIngredients)) {
                // 실제 매칭된 사용자 재료명을 찾아서 추가
                val matchedUserIngredient = findMatchedUserIngredient(cleanIngredient, userIngredients)
                if (matchedUserIngredient != null) {
                    matched.add(matchedUserIngredient)
                }
            }
        }

        return matched.distinct() // 중복 제거
    }

    /**
     * 레시피 재료가 사용자 재료와 매칭되는지 확인
     */
    private fun isIngredientMatched(recipeIngredient: String, userIngredients: List<String>): Boolean {
        // 1. 직접 매칭 (대소문자 무시)
        if (userIngredients.any { it.equals(recipeIngredient, ignoreCase = true) }) {
            return true
        }

        // 2. 부분 매칭 (사용자 재료가 레시피 재료에 포함되는 경우)
        if (userIngredients.any { recipeIngredient.contains(it, ignoreCase = true) }) {
            return true
        }

        // 3. 괄호 처리: "돼지고기(또는 참치)" 같은 경우
        if (recipeIngredient.contains("(") && recipeIngredient.contains(")")) {
            val alternatives = extractAlternatives(recipeIngredient)
            return alternatives.any { alternative ->
                userIngredients.any { userIngredient ->
                    alternative.equals(userIngredient, ignoreCase = true) ||
                            alternative.contains(userIngredient, ignoreCase = true) ||
                            userIngredient.contains(alternative, ignoreCase = true)
                }
            }
        }

        // 4. 역방향 매칭 (레시피 재료가 사용자 재료에 포함되는 경우)
        if (userIngredients.any { it.contains(recipeIngredient, ignoreCase = true) }) {
            return true
        }

        return false
    }

    /**
     * 실제 매칭된 사용자 재료명을 찾아 반환
     */
    private fun findMatchedUserIngredient(recipeIngredient: String, userIngredients: List<String>): String? {
        // 1. 직접 매칭
        userIngredients.find { it.equals(recipeIngredient, ignoreCase = true) }?.let { return it }

        // 2. 사용자 재료가 레시피 재료에 포함되는 경우
        userIngredients.find { recipeIngredient.contains(it, ignoreCase = true) }?.let { return it }

        // 3. 괄호 처리
        if (recipeIngredient.contains("(") && recipeIngredient.contains(")")) {
            val alternatives = extractAlternatives(recipeIngredient)
            for (alternative in alternatives) {
                userIngredients.find { userIngredient ->
                    alternative.equals(userIngredient, ignoreCase = true) ||
                            alternative.contains(userIngredient, ignoreCase = true) ||
                            userIngredient.contains(alternative, ignoreCase = true)
                }?.let { return it }
            }
        }

        // 4. 역방향 매칭
        userIngredients.find { it.contains(recipeIngredient, ignoreCase = true) }?.let { return it }

        return null
    }

    /**
     * 괄호 안의 대체 재료들을 추출
     * 예: "돼지고기(또는 참치)" -> ["돼지고기", "참치"]
     */
    private fun extractAlternatives(ingredient: String): List<String> {
        val alternatives = mutableListOf<String>()

        // 괄호 밖의 주 재료 추가
        val mainIngredient = ingredient.substringBefore("(").trim()
        if (mainIngredient.isNotEmpty()) {
            alternatives.add(mainIngredient)
        }

        // 괄호 안의 내용 처리
        val parenthesesContent = ingredient.substringAfter("(").substringBefore(")").trim()
        if (parenthesesContent.isNotEmpty()) {
            // "또는", "혹은", "," 등으로 분리
            val separators = listOf("또는", "혹은", "이나", ",", "/", "｜")
            var content = parenthesesContent

            for (separator in separators) {
                if (content.contains(separator)) {
                    val parts = content.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
                    alternatives.addAll(parts)
                    break
                }
            }

            // 분리되지 않았다면 전체를 하나의 대체재로 추가
            if (!separators.any { content.contains(it) }) {
                alternatives.add(content)
            }
        }

        return alternatives.distinct().filter { it.isNotEmpty() }
    }
}