package com.whattoeat.domain.notification.entity

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
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

@Entity
@Table(
    name = "notification",
    indexes = [
        Index(name = "idx_notification_receiver_id", columnList = "receiver_id"),
        Index(name = "idx_notification_receiver_id_id", columnList = "receiver_id, id"),
        Index(name = "idx_notification_dedup", columnList = "receiver_id, actor_id, type, feed_id"),
    ]
)
class Notification private constructor(
    receiver: User,
    actor: User,
    feed: Feed?,
    restaurantList: RestaurantList?,
    type: NotificationType,
    message: String
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    var receiver: User = receiver
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    var actor: User = actor
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id")
    var feed: Feed? = feed
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_list_id")
    var restaurantList: RestaurantList? = restaurantList
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var type: NotificationType = type
        protected set

    @Column(length = 500, nullable = false)
    var message: String = message
        protected set

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false
        protected set

    fun markAsRead() {
        this.isRead = true
    }

    companion object {
        @JvmStatic
        fun of(receiver: User, actor: User, feed: Feed?, type: NotificationType, message: String): Notification =
            Notification(receiver, actor, feed, null, type, message)

        @JvmStatic
        fun of(
            receiver: User,
            actor: User,
            feed: Feed?,
            restaurantList: RestaurantList?,
            type: NotificationType,
            message: String
        ): Notification =
            Notification(receiver, actor, feed, restaurantList, type, message)
    }
}
