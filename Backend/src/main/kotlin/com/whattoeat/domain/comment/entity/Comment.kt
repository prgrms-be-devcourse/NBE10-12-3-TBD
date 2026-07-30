package com.whattoeat.domain.comment.entity

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "feed_comment")
class Comment(
    feed: Feed,
    user: User,
    content: String,
) : BaseEntity() {
    @field:JoinColumn(name = "feed_id", nullable = false)
    @field:ManyToOne(fetch = FetchType.LAZY)
    var feed: Feed = feed
        protected set

    @field:JoinColumn(name = "user_id", nullable = false)
    @field:ManyToOne(fetch = FetchType.LAZY)
    var user: User = user
        protected set

    @field:Column(nullable = false, length = 500)
    var content: String = content
        protected set
}
