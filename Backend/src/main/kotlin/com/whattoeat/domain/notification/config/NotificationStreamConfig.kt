package com.whattoeat.domain.notification.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.whattoeat.domain.notification.messaging.NotificationStreamPublisher
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.core.RedisTemplate

@Configuration
class NotificationStreamConfig(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Spring Boot의 JacksonAutoConfiguration이 활성화되지 않은 경우를 대비한 fallback bean
    @Bean
    @ConditionalOnMissingBean(ObjectMapper::class)
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    @PostConstruct
    fun ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream<String, String>().createGroup(
                NotificationStreamPublisher.STREAM_KEY,
                ReadOffset.from("0"),
                NotificationStreamPublisher.CONSUMER_GROUP
            )
            log.info(
                "Consumer group '{}' created on stream '{}'",
                NotificationStreamPublisher.CONSUMER_GROUP,
                NotificationStreamPublisher.STREAM_KEY
            )
        } catch (ex: RedisSystemException) {
            if (ex.cause?.message?.contains("BUSYGROUP") == true) {
                log.debug("Consumer group '{}' already exists", NotificationStreamPublisher.CONSUMER_GROUP)
            } else {
                log.warn("Consumer group 생성 실패 (RedisSystemException): {}", ex.message)
            }
        } catch (ex: Exception) {
            // Redis 미기동 등 상황에서 앱 자체는 뜨도록 삼킨다
            log.warn("Consumer group 생성 실패 (Redis 상태 확인 필요): {}", ex.message)
        }
    }
}
