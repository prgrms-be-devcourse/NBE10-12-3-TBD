package com.whattoeat.domain.restaurant.mapper

import com.whattoeat.domain.restaurant.entity.Category

private val TOKEN_TO_CATEGORY: Map<String, Category> = mapOf(
    "한식" to Category.KOREAN,
    "중식" to Category.CHINESE,
    "일식" to Category.JAPANESE,
    "양식" to Category.WESTERN,
    "패밀리레스토랑" to Category.WESTERN,
    "아시아음식" to Category.ASIAN,
    "분식" to Category.SNACK,
    "간식" to Category.SNACK,
    "카페" to Category.CAFE,
    "디저트" to Category.CAFE,
    "치킨" to Category.CHICKEN,
    "패스트푸드" to Category.FASTFOOD,
    "술집" to Category.BAR,
    "뷔페" to Category.BUFFET,
    "샤브샤브" to Category.SHABU,
    "퓨전요리" to Category.FUSION
)

// "음식점 > 치킨 > 교촌치킨" -> "치킨", "카페 > 커피전문점" -> "커피전문점"
private fun extractToken(categoryName: String?): String? {
    val parts = categoryName.orEmpty().split(">").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    return if (parts[0] == "음식점") parts.getOrNull(1) else parts[0]
}

fun toCategory(categoryName: String?): Category {
    val token = extractToken(categoryName) ?: return Category.ETC
    return TOKEN_TO_CATEGORY[token] ?: Category.ETC
}

fun categoryLabel(categoryName: String?): String {
    val parts = categoryName.orEmpty()
        .split(">")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (parts.isEmpty()) return "기타"

    return when {
        parts[0] == "카페" ->
            parts.getOrNull(1) ?: "카페"

        parts[0] == "음식점" && parts.getOrNull(1) == "카페" ->
            parts.getOrNull(2) ?: "카페"

        parts[0] == "음식점" ->
            parts.getOrNull(1) ?: "기타"

        else -> parts[0]
    }
}
