package com.whattoeat.domain.restaurantlist.entity

import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
        name = "saved_restaurant_list",
        uniqueConstraints = [
                UniqueConstraint(
                        name = "uk_saved_restaurant_list_user_list",
                        columnNames = ["user_id", "restaurant_list_id"]
                )
        ]
)
class SavedRestaurantList protected constructor() : BaseEntity() {
    // 저장한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    // 저장된 원본 리스트
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_list_id", nullable = false)
    lateinit var restaurantList: RestaurantList
        protected set

    constructor(
        user: User,
        restaurantList: RestaurantList
    ) : this() {
        this.user = user
        this.restaurantList = restaurantList
    }
}
