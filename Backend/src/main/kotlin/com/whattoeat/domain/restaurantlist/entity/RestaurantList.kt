package com.whattoeat.domain.restaurantlist.entity

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.*
import org.springframework.data.annotation.LastModifiedDate

import java.time.LocalDateTime
import java.util.ArrayList

@Entity
@Table(name = "restaurant_list",
    indexes = [
        Index(name = "idx_restaurant_list_user_id", columnList = "user_id")
    ]
)
class RestaurantList protected constructor() : BaseEntity() {
    // 리스트 만든 사람
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    // 리스트 제목
    @Column(name = "title", nullable = false, length = 200)
    lateinit var title: String
        protected set

    // 리스트 설명
    @Column(name = "description", nullable = false, length = 500)
    lateinit var description: String
        protected set

    // 분위기 태그
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "mood_tag", length = 50)
    var moodTag: MoodTag? = null
        protected set

    // 수정일
    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
        protected set

    @OneToMany(mappedBy = "restaurantList", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    var items: MutableList<RestaurantListItem> = ArrayList()
        protected set

    constructor(
        user: User,
        title: String,
        description: String,
        moodTag: MoodTag?
    ) : this() {
        this.user = user
        this.title = title
        this.description = description
        this.moodTag = moodTag
    }

    fun update(
        title: String,
        description: String,
        moodTag: MoodTag?
    ) {
        this.title = title;
        this.description = description
        this.moodTag = moodTag
    }
}
