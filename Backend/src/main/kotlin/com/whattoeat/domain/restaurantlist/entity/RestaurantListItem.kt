package com.whattoeat.domain.restaurantlist.entity

import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "restaurant_list_item",
    uniqueConstraints = [
      UniqueConstraint(
              name = "uk_restaurant_list_item_list_restaurant",
              columnNames = ["list_id", "restaurant_id"]
      )
    ],
    indexes = [
        Index(name = "idx_restaurant_list_item_list_id", columnList = "list_id"),
        Index(name = "idx_restaurant_list_item_restaurant_id", columnList = "restaurant_id")
    ]
)
class RestaurantListItem protected constructor() : BaseEntity() {
    // 레스토랑 리스트 번호
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    lateinit var restaurantList: RestaurantList
        protected set

    // 식당 번호
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    lateinit var restaurant: Restaurant
        protected set

    // 순서
    @Column(name = "order_index", nullable = false)
    var orderIndex: Int = 0
        protected set

    // 한줄평
    @Column(name = "memo", length = 200)
    var memo: String? = null
        protected set

    constructor(
        restaurantList: RestaurantList,
        restaurant: Restaurant,
        memo: String?,
        orderIndex: Int?
    ) : this() {
        this.restaurantList = restaurantList
        this.restaurant = restaurant
        this.memo = memo
        if (orderIndex != null) {
            this.orderIndex = orderIndex
        }
    }

    fun updateListItem(
        orderIndex: Int?,
        memo: String?,
    ) {
        if (orderIndex != null) {
            this.orderIndex = orderIndex
        }
        this.memo = memo
    }
}
