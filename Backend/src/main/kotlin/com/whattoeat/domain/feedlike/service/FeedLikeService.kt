package com.whattoeat.domain.feedlike.service

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.feedlike.dto.FeedLikeResponse
import com.whattoeat.domain.feedlike.entity.FeedLike
import com.whattoeat.domain.feedlike.repository.FeedLikeRepository
import com.whattoeat.domain.notification.event.FeedLikedEvent
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.AlreadyLikedFeedException
import com.whattoeat.global.exception.FeedLikeNotFoundException
import com.whattoeat.global.exception.FeedNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FeedLikeService(
    private val feedLikeRepository: FeedLikeRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun like(userId: Long, feedId: Long): FeedLikeResponse {
        val user = getUser(userId)
        val feed = getFeed(feedId)

        if (feedLikeRepository.existsByFeed_IdAndUser_Id(feedId, userId)) {
            throw AlreadyLikedFeedException()
        }

        feedLikeRepository.save(FeedLike.of(feed, user))

        feed.increaseLikeCount()

        eventPublisher.publishEvent(FeedLikedEvent(feed.id!!, userId))

        return FeedLikeResponse.of(feed.id, feed.likeCount, true)
    }

    @Transactional
    fun unlike(userId: Long, feedId: Long): FeedLikeResponse {
        getUser(userId)
        val feed = getFeed(feedId)

        val feedLike =
            feedLikeRepository.findByFeed_IdAndUser_Id(feedId, userId)
                .orElseThrow { FeedLikeNotFoundException() }

        feedLikeRepository.delete(feedLike)

        feed.decreaseLikeCount()

        return FeedLikeResponse.of(feed.id, feed.likeCount, false)
    }

    @Transactional(readOnly = true)
    fun getLikeStatus(userId: Long, feedId: Long): FeedLikeResponse {
        getUser(userId)
        val feed = getFeed(feedId)

        val liked = feedLikeRepository.existsByFeed_IdAndUser_Id(feedId, userId)

        return FeedLikeResponse.of(feed.id, feed.likeCount, liked)
    }

    private fun getUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

    private fun getFeed(feedId: Long): Feed =
        feedRepository.findById(feedId)
            .orElseThrow { FeedNotFoundException(feedId) }
}
