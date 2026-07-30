package com.whattoeat.domain.follow.repository

import com.whattoeat.domain.follow.entity.Follow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface FollowRepository : JpaRepository<Follow, Long> {
    fun findByFollower_IdAndFollowing_Id(followerId: Long, followingId: Long): Optional<Follow>

    fun existsByFollower_IdAndFollowing_Id(followerId: Long, followingId: Long): Boolean

    @EntityGraph(attributePaths = ["following"])
    fun findByFollower_Id(followerId: Long, pageable: Pageable): Page<Follow>

    @EntityGraph(attributePaths = ["follower"])
    fun findByFollowing_Id(followingId: Long, pageable: Pageable): Page<Follow>

    fun deleteByFollower_IdAndFollowing_Id(followerId: Long, followingId: Long)

    fun countByFollower_Id(followerId: Long): Long

    fun countByFollowing_Id(followingId: Long): Long
}
