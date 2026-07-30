package com.whattoeat.domain.restaurant.entity

import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(name = "restaurant",
    indexes = [
        Index(name = "idx_restaurant_category", columnList = "category"),
        Index(name = "idx_restaurant_region1", columnList = "region1"),
        Index(name = "idx_restaurant_region2", columnList = "region2"),
        Index(name = "idx_restaurant_region3", columnList = "region3"),
        Index(name = "idx_restaurant_region4", columnList = "region4")
    ]
)
class Restaurant protected constructor() : BaseEntity() {

    @Column(name = "kakao_place_id", nullable = false, unique = true, length = 255)
    lateinit var kakaoPlaceId: String
        protected set

    @Column(name = "name", nullable = false, length = 200)
    lateinit var name: String
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 100)
    lateinit var category: Category
        protected set

    @Column(name = "address", nullable = false, length = 300)
    lateinit var address: String
        protected set

    @Column(name = "road_address", length = 300)
    var roadAddress: String? = null
        protected set

    @Column(name = "region1", nullable = false, length = 50)
    lateinit var region1: String
        protected set

    @Column(name = "region2", nullable = false, length = 50)
    lateinit var region2: String
        protected set

    @Column(name = "region3", length = 50)
    var region3: String? = null
        protected set

    @Column(name = "region4", length = 50)
    var region4: String? = null
        protected set

    @Column(name = "phone", length = 50)
    var phone: String? = null
        protected set

    @Column(name = "lat", nullable = false)
    var lat: Double = 0.0
        protected set

    @Column(name = "lng", nullable = false)
    var lng: Double = 0.0
        protected set

    constructor(
        kakaoPlaceId: String,
        name: String,
        category: Category,
        address: String,
        roadAddress: String?,
        region1: String,
        region2: String,
        region3: String?,
        region4: String?,
        phone: String?,
        lat: Double,
        lng: Double
    ) :this() {
        this.kakaoPlaceId = kakaoPlaceId;
        this.name = name;
        this.category = category;
        this.address = address;
        this.roadAddress = roadAddress;
        this.region1 = region1;
        this.region2 = region2;
        this.region3 = region3;
        this.region4 = region4;
        this.phone = phone;
        this.lat = lat;
        this.lng = lng;
    }

}
