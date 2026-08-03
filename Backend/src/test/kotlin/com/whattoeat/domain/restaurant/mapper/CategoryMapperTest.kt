package com.whattoeat.domain.restaurant.mapper

import com.whattoeat.domain.restaurant.entity.Category
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class CategoryMapperTest {

    @Test
    fun `toCategory - 실측된 카카오 category_name 매핑`() {
        Assertions.assertThat(toCategory("음식점 > 한식 > 육류,고기")).isEqualTo(Category.KOREAN)
        Assertions.assertThat(toCategory("음식점 > 한식 > 육류,고기 > 족발,보쌈")).isEqualTo(Category.KOREAN)
        Assertions.assertThat(toCategory("음식점 > 중식 > 중국요리")).isEqualTo(Category.CHINESE)
        Assertions.assertThat(toCategory("음식점 > 일식 > 돈까스,우동")).isEqualTo(Category.JAPANESE)
        Assertions.assertThat(toCategory("음식점 > 양식 > 피자")).isEqualTo(Category.WESTERN)
        Assertions.assertThat(toCategory("음식점 > 패밀리레스토랑 > 애슐리")).isEqualTo(Category.WESTERN)
        Assertions.assertThat(toCategory("음식점 > 아시아음식 > 동남아음식")).isEqualTo(Category.ASIAN)
        Assertions.assertThat(toCategory("음식점 > 분식")).isEqualTo(Category.SNACK)
        Assertions.assertThat(toCategory("음식점 > 간식 > 제과,베이커리")).isEqualTo(Category.SNACK)
        Assertions.assertThat(toCategory("음식점 > 치킨")).isEqualTo(Category.CHICKEN)
        Assertions.assertThat(toCategory("음식점 > 치킨 > 교촌치킨")).isEqualTo(Category.CHICKEN)
        Assertions.assertThat(toCategory("음식점 > 패스트푸드 > 맥도날드")).isEqualTo(Category.FASTFOOD)
        Assertions.assertThat(toCategory("음식점 > 술집 > 호프,요리주점")).isEqualTo(Category.BAR)
        Assertions.assertThat(toCategory("음식점 > 술집")).isEqualTo(Category.BAR)
        Assertions.assertThat(toCategory("음식점 > 카페 > 테마카페")).isEqualTo(Category.CAFE)
        Assertions.assertThat(toCategory("카페 > 커피전문점")).isEqualTo(Category.CAFE)
        Assertions.assertThat(toCategory("음식점 > 뷔페")).isEqualTo(Category.BUFFET)
        Assertions.assertThat(toCategory("음식점 > 샤브샤브")).isEqualTo(Category.SHABU)
        Assertions.assertThat(toCategory("음식점 > 퓨전요리")).isEqualTo(Category.FUSION)
        Assertions.assertThat(toCategory("여가시설 > 만화방")).isEqualTo(Category.ETC)
        Assertions.assertThat(toCategory(null)).isEqualTo(Category.ETC)
        Assertions.assertThat(toCategory("")).isEqualTo(Category.ETC)
    }

    @Test
    fun `categoryLabel - 음식점은 중분류를 반환한다`() {
        Assertions.assertThat(categoryLabel("음식점 > 치킨"))
            .isEqualTo("치킨")
        Assertions.assertThat(categoryLabel("음식점 > 한식 > 육류,고기"))
            .isEqualTo("한식")
        Assertions.assertThat(categoryLabel("음식점 > 술집 > 호프,요리주점"))
            .isEqualTo("술집")
    }

    @Test
    fun `categoryLabel - 카페는 세부 분류를 반환한다`() {
        Assertions.assertThat(categoryLabel("카페 > 커피전문점"))
            .isEqualTo("커피전문점")
        Assertions.assertThat(categoryLabel("카페 > 전통찻집"))
            .isEqualTo("전통찻집")
        Assertions.assertThat(categoryLabel("음식점 > 카페 > 테마카페"))
            .isEqualTo("테마카페")
    }

    @Test
    fun `categoryLabel - 세부 분류가 없는 카페는 카페를 반환한다`() {
        Assertions.assertThat(categoryLabel("카페"))
            .isEqualTo("카페")
        Assertions.assertThat(categoryLabel("음식점 > 카페"))
            .isEqualTo("카페")
    }

    @Test
    fun `categoryLabel - 값이 없으면 기타를 반환한다`() {
        Assertions.assertThat(categoryLabel(null))
            .isEqualTo("기타")
        Assertions.assertThat(categoryLabel(""))
            .isEqualTo("기타")
        Assertions.assertThat(categoryLabel("  "))
            .isEqualTo("기타")
    }

}
