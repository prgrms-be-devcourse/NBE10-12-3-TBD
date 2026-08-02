package com.whattoeat.domain.feedlike.entity

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "feed_like",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_feed_like_feed_user",
            columnNames = ["feed_id", "user_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_feed_like_feed_id", columnList = "feed_id"),
        Index(name = "idx_feed_like_user_id", columnList = "user_id"),
    ],
)
class FeedLike protected constructor() : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    lateinit var feed: Feed
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    private constructor(feed: Feed, user: User) : this() {
        this.feed = feed
        this.user = user
    }

    companion object {
        @JvmStatic
        fun of(feed: Feed, user: User): FeedLike = FeedLike(feed, user)
    }
}
