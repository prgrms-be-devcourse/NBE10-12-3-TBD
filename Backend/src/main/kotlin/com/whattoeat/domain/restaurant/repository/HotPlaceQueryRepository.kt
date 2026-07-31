package com.whattoeat.domain.restaurant.repository

import com.whattoeat.domain.restaurant.entity.Restaurant
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface HotPlaceQueryRepository : Repository<Restaurant, Long> {

    @Query(
        value = """
            SELECT restaurant_id
            FROM (
                SELECT f.restaurant_id AS restaurant_id, CAST(:wLike AS SIGNED) AS w
                FROM feed_like fl
                JOIN feeds f ON f.id = fl.feed_id
                WHERE fl.created_at >= :since
                  AND f.restaurant_id IS NOT NULL

                UNION ALL

                SELECT rli.restaurant_id AS restaurant_id, CAST(:wSave AS SIGNED) AS w
                FROM restaurant_list_item rli
                WHERE rli.created_at >= :since

                UNION ALL

                SELECT f2.restaurant_id AS restaurant_id, CAST(:wFeed AS SIGNED) AS w
                FROM feeds f2
                WHERE f2.created_at >= :since
                  AND f2.restaurant_id IS NOT NULL
            ) t
            GROUP BY restaurant_id
            ORDER BY SUM(w) DESC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    fun findTopRestaurantIdsSince(
        @Param("since") since: LocalDateTime,
        @Param("wLike") wLike: Int,
        @Param("wSave") wSave: Int,
        @Param("wFeed") wFeed: Int,
        @Param("limit") limit: Int
    ): List<Long>

    @Query(
        value = """
            SELECT restaurant_id
            FROM (
                SELECT f.restaurant_id AS restaurant_id, CAST(:wLike AS SIGNED) AS w
                FROM feed_like fl
                JOIN feeds f ON f.id = fl.feed_id
                WHERE f.restaurant_id IS NOT NULL
                  AND f.restaurant_id NOT IN (:excludeIds)

                UNION ALL

                SELECT rli.restaurant_id AS restaurant_id, CAST(:wSave AS SIGNED) AS w
                FROM restaurant_list_item rli
                WHERE rli.restaurant_id NOT IN (:excludeIds)

                UNION ALL

                SELECT f2.restaurant_id AS restaurant_id, CAST(:wFeed AS SIGNED) AS w
                FROM feeds f2
                WHERE f2.restaurant_id IS NOT NULL
                  AND f2.restaurant_id NOT IN (:excludeIds)
            ) t
            GROUP BY restaurant_id
            ORDER BY SUM(w) DESC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    fun findTopRestaurantIdsCumulative(
        @Param("excludeIds") excludeIds: List<Long>,
        @Param("wLike") wLike: Int,
        @Param("wSave") wSave: Int,
        @Param("wFeed") wFeed: Int,
        @Param("limit") limit: Int
    ): List<Long>
}
