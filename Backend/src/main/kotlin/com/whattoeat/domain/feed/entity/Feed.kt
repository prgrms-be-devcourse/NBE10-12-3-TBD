package com.whattoeat.domain.feed.entity

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.springframework.data.annotation.LastModifiedDate

@Entity
@Table(
    name = "feeds",
    indexes = [
        Index(name = "idx_feed_user_id", columnList = "user_id"),
        Index(name = "idx_feed_restaurant_id", columnList = "restaurant_id"),
    ],
)
class Feed protected constructor() : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
     lateinit var user: User
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
 var restaurant: Restaurant? = null

    @Column(length = 1000, nullable = false)
   lateinit var content: String

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
      var updatedAt: LocalDateTime? = null
        protected set

    @Column(nullable = false)
     var likeCount: Int = 0
        protected set

     var imageUrl: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "mood_tag", length = 20)
    var moodTag: MoodTag? = null


    constructor(
        user: User,
        restaurant: Restaurant?,
        content: String,
        imageUrl: String?,
        likeCount: Int?,
        moodTag: MoodTag?,
    ) : this() {
        this.user = user
        this.restaurant = restaurant
        this.content = content
        this.imageUrl = imageUrl
        this.likeCount = likeCount ?: 0
        this.moodTag = moodTag
    }

     fun update(content: String, restaurant: Restaurant?, imageUrl: String?) {
        this.content = content
        this.restaurant = restaurant
        this.imageUrl = imageUrl
    }

     fun increaseLikeCount() {
        likeCount++
    }

     fun decreaseLikeCount() {
        if (likeCount > 0) {
            likeCount--
        }
    }

    class FeedBuilder internal constructor() {
        private var user: User? = null
        private var restaurant: Restaurant? = null
        private var content: String? = null
        private var imageUrl: String? = null
        private var likeCount: Int? = null
        private var moodTag: MoodTag? = null

        fun user(user: User?): FeedBuilder = apply { this.user = user }

        fun restaurant(restaurant: Restaurant?): FeedBuilder = apply { this.restaurant = restaurant }

        fun content(content: String?): FeedBuilder = apply { this.content = content }

        fun imageUrl(imageUrl: String?): FeedBuilder = apply { this.imageUrl = imageUrl }

        fun likeCount(likeCount: Int?): FeedBuilder = apply { this.likeCount = likeCount }

        fun moodTag(moodTag: MoodTag?): FeedBuilder = apply { this.moodTag = moodTag }

        fun build(): Feed =
            Feed(
                user = requireNotNull(user),
                restaurant = restaurant,
                content = requireNotNull(content),
                imageUrl = imageUrl,
                likeCount = likeCount,
                moodTag = moodTag,
            )
    }

    companion object {
        @JvmStatic
        fun builder(): FeedBuilder = FeedBuilder()
    }
}
