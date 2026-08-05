package com.whattoeat.domain.follow.service

import com.whattoeat.domain.follow.entity.Follow
import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.FollowedEvent
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.AlreadyFollowingException
import com.whattoeat.global.exception.FollowNotFoundException
import com.whattoeat.global.exception.SelfFollowNotAllowedException
import com.whattoeat.global.exception.UserNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service

class FollowService(
    private val followRepository: FollowRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val notificationRepository: NotificationRepository,
) {
    @Transactional
    fun follow(followerId: Long, followingId: Long): Follow {
        validateSelfFollow(followerId, followingId)

        val follower: User = getUser(followerId)
        val following: User= getUser(followingId)

        if (followRepository.existsByFollower_IdAndFollowing_Id(followerId, followingId)) {
            throw AlreadyFollowingException()
        }

        val saved = followRepository.save(Follow.of(follower, following))
        eventPublisher.publishEvent(FollowedEvent(followerId, followingId))
        return saved
    }

    @Transactional
    fun unfollow(followerId: Long, followingId: Long) {
        getUser(followerId)
        getUser(followingId)

        val follow :Follow = followRepository.findByFollower_IdAndFollowing_Id(followerId, followingId)
            .orElseThrow { FollowNotFoundException() }

        followRepository.delete(follow)

        // 언팔로우 시 해당 팔로우 알림도 지워서, 나중에 다시 팔로우하면 알림이 다시 가도록 한다.
        notificationRepository.deleteByReceiverIdAndActorIdAndTypeAndFeedIsNullAndRestaurantListIsNull(
            followingId, followerId, NotificationType.FOLLOW
        )
    }

    @Transactional(readOnly = true)
    fun getFollowings(userId: Long, pageable: Pageable): Page<Follow> {
        getUser(userId)
        return followRepository.findByFollower_Id(userId, pageable)
    }

    @Transactional(readOnly = true)
    fun getFollowers(userId: Long, pageable: Pageable): Page<Follow> {
        getUser(userId)
        return followRepository.findByFollowing_Id(userId, pageable)
    }

    @Transactional(readOnly = true)
    fun getFollowerCount(userId: Long): Long {
        getUser(userId)
        return followRepository.countByFollowing_Id(userId)
    }

    @Transactional(readOnly = true)
    fun getFollowingCount(userId: Long): Long {
        getUser(userId)
        return followRepository.countByFollower_Id(userId)
    }

    @Transactional(readOnly = true)
    fun isFollowing(followerId: Long, followingId: Long): Boolean {
        return followRepository.existsByFollower_IdAndFollowing_Id(followerId, followingId)
    }

    private fun validateSelfFollow(followerId: Long, followingId: Long) {
        if (followerId == followingId) {
            throw SelfFollowNotAllowedException()
        }
    }

    private fun getUser(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow{ UserNotFoundException(userId) }
    }
}
