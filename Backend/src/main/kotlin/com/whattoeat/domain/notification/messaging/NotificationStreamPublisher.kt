package com.whattoeat.domain.notification.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.notification.entity.NotificationType
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class NotificationStreamPublisher(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    fun publish(type: NotificationType, payload: Any) {
        val record = MapRecord.create(
            STREAM_KEY,
            mapOf(
                "type" to type.name,
                "payload" to objectMapper.writeValueAsString(payload),
                "publishedAt" to LocalDateTime.now().toString()
            )
        )
        redisTemplate.opsForStream<String, String>().add(record)
    }

    companion object {
        const val STREAM_KEY = "notification-stream"
        const val DLQ_KEY = "notification-stream-dlq"
        const val CONSUMER_GROUP = "notification-workers"
    }
}
