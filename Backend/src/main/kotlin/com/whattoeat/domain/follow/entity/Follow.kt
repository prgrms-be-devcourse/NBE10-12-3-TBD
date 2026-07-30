package com.whattoeat.domain.follow.entity

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
    name = "follow",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_follow_follower_following",
            columnNames = ["follower_id", "following_id"]
        )
    ],
    indexes = [
        Index(name = "idx_follow_follower_id", columnList = "follower_id"),
        Index(name = "idx_follow_following_id", columnList = "following_id")
    ]
)
open class Follow protected constructor() : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    lateinit var follower: User
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    lateinit var following: User
        protected set

    private constructor(
        follower: User,
        following: User
    ) : this() {
        this.follower = follower
        this.following = following
    }

    companion object {
        @JvmStatic
        fun of(follower: User, following: User): Follow {
            return Follow(follower, following)
        }
    }
}