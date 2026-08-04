package com.whattoeat.domain.recommendation.service

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag

data class MoodRule(
    val preferredCategories: Set<Category>,
    val keywords: Set<String>
)

val MOOD_RULES: Map<MoodTag, MoodRule> = mapOf(
    MoodTag.DATE to MoodRule(
        setOf(Category.CAFE, Category.WESTERN, Category.JAPANESE),
        setOf("이탈리안", "스테이크", "초밥", "테마카페", "전통찻집", "와인", "브런치", "이자카야")
    ),
    MoodTag.SOLO to MoodRule(
        setOf(Category.SNACK, Category.FASTFOOD, Category.KOREAN),
        setOf("해장국", "냉면", "국수", "분식", "떡볶이", "김밥", "국밥", "덮밥", "햄버거")
    ),
    MoodTag.GROUP to MoodRule(
        setOf(Category.KOREAN, Category.BAR, Category.CHICKEN),
        setOf("육류,고기", "갈비", "곱창", "호프", "요리주점", "포장마차", "이자카야", "닭요리")
    ),
    MoodTag.NIGHT to MoodRule(
        setOf(Category.CHICKEN, Category.SNACK, Category.BAR),
        setOf("치킨", "닭요리", "호프", "포장마차", "떡볶이", "곱창")
    ),
    MoodTag.FAMILY to MoodRule(
        setOf(Category.KOREAN, Category.BUFFET, Category.WESTERN),
        setOf("한정식", "뷔페", "갈비", "쌈밥", "감자탕")
    ),
    MoodTag.FRIENDS to MoodRule(
        setOf(Category.CHICKEN, Category.BAR, Category.ASIAN),
        setOf("호프", "요리주점", "포장마차", "이자카야", "마라탕", "양꼬치", "치킨")
    )
)

fun moodRuleScore(mood: MoodTag?, category: Category, name: String, categoryName: String?): Int {
    if (mood == null) return 0
    val rule = MOOD_RULES[mood] ?: return 0
    var score = 0
    if (category in rule.preferredCategories) score += 1
    val haystack = "$name ${categoryName.orEmpty()}"
    score += rule.keywords.count { it in haystack }
    return score
}
