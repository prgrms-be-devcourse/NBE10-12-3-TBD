package com.whattoeat.domain.notification.messaging

import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.messaging.handler.NotificationHandlerRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Component
class NotificationWorker(
    private val redisTemplate: RedisTemplate<String, String>,
    private val handlerRegistry: NotificationHandlerRegistry
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(javaClass)
    private val consumerName: String = buildConsumerName()
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    override fun start() {
        running.set(true)
        thread = Thread(::loop, "notification-worker").apply {
            isDaemon = true
            start()
        }
        log.info("NotificationWorker started as {}", consumerName)
    }

    override fun stop() {
        running.set(false)
        thread?.interrupt()
        thread?.join(10_000)
        log.info("NotificationWorker stopped")
    }

    override fun isRunning(): Boolean = running.get()

    private fun loop() {
        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                pollAndProcess()
                reclaimStaleMessages()
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (ex: Exception) {
                log.error("워커 루프 에러", ex)
                runCatching { Thread.sleep(1_000) }
            }
        }
    }

    private fun pollAndProcess() {
        val records: List<MapRecord<String, Any, Any>>? = redisTemplate.opsForStream<Any, Any>().read(
            Consumer.from(CONSUMER_GROUP, consumerName),
            StreamReadOptions.empty().count(10).block(Duration.ofSeconds(5)),
            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
        )
        records?.forEach { processOne(toStringRecord(it)) }
    }

    private fun toStringRecord(record: MapRecord<String, Any, Any>): MapRecord<String, String, String> {
        val map: Map<String, String> = record.value.entries.associate { (k, v) -> k.toString() to v.toString() }
        return MapRecord.create(record.stream ?: STREAM_KEY, map).withId(record.id)
    }

    private fun processOne(record: MapRecord<String, String, String>) {
        val fields = record.value
        val typeName = fields["type"]
        if (typeName == null) {
            log.warn("type 필드 누락, DLQ 이동 recordId={}", record.id)
            moveToDlq(record, reason = "MISSING_TYPE")
            ack(record)
            return
        }
        val payload = fields["payload"]
        if (payload == null) {
            log.warn("payload 필드 누락, DLQ 이동 recordId={}", record.id)
            moveToDlq(record, reason = "MISSING_PAYLOAD")
            ack(record)
            return
        }

        try {
            val type = NotificationType.valueOf(typeName)
            val handler = handlerRegistry.forType(type)
            handler.handle(payload)
            ack(record)
        } catch (ex: Exception) {
            log.warn("알림 처리 실패 recordId={} type={} : {}", record.id, typeName, ex.message)
            // XACK 하지 않음 → Pending 유지 → reclaim 에서 재시도
        }
    }

    private fun reclaimStaleMessages() {
        // 재시작마다 consumerName의 UUID가 바뀌므로 자기 pending만 조회하면
        // 이전 워커가 남긴 pending을 못 봐서 유실된다.
        // → 컨슈머 그룹 전체 pending을 조회하고, idle time(30s) 넘은 메시지를
        //   현재 consumer로 XCLAIM 해서 재처리한다.
        val pendingMessages = try {
            redisTemplate.opsForStream<Any, Any>().pending(
                STREAM_KEY,
                CONSUMER_GROUP,
                Range.unbounded<String>(),
                10L
            )
        } catch (ex: Exception) {
            return
        } ?: return

        pendingMessages.forEach { pm ->
            val idStr = pm.idAsString
            val deliveryCount = pm.totalDeliveryCount

            if (deliveryCount > MAX_RETRY) {
                val original = fetchOne(idStr)
                if (original != null) {
                    moveToDlq(original, reason = "MAX_RETRY_EXCEEDED (count=$deliveryCount)")
                    ack(original)
                }
                log.error("알림 처리 재시도 초과 → DLQ recordId={} deliveryCount={}", idStr, deliveryCount)
            } else {
                // XCLAIM의 min-idle-time(30초)로 필터 → 방금 다른 워커가 잡은 신선한 메시지는 스킵
                val claimed = redisTemplate.opsForStream<Any, Any>().claim(
                    STREAM_KEY, CONSUMER_GROUP, consumerName,
                    Duration.ofSeconds(30),
                    RecordId.of(idStr)
                )
                claimed?.forEach { processOne(toStringRecord(it)) }
            }
        }
    }

    private fun ack(record: MapRecord<String, String, String>) {
        redisTemplate.opsForStream<Any, Any>()
            .acknowledge(STREAM_KEY, CONSUMER_GROUP, record.id)
    }

    private fun moveToDlq(record: MapRecord<String, String, String>, reason: String) {
        val dlqRecord = MapRecord.create(
            DLQ_KEY,
            record.value + mapOf(
                "originalId" to record.id.toString(),
                "failReason" to reason,
                "movedAt" to LocalDateTime.now().toString()
            )
        )
        redisTemplate.opsForStream<String, String>().add(dlqRecord)
    }

    private fun fetchOne(idStr: String): MapRecord<String, String, String>? =
        redisTemplate.opsForStream<Any, Any>()
            .range(STREAM_KEY, Range.just(idStr))
            ?.firstOrNull()
            ?.let(::toStringRecord)

    private fun buildConsumerName(): String {
        val host = try {
            InetAddress.getLocalHost().hostName
        } catch (ex: Exception) {
            "unknown"
        }
        return "worker-$host-${UUID.randomUUID()}"
    }

    companion object {
        const val MAX_RETRY = 3
        val STREAM_KEY = NotificationStreamPublisher.STREAM_KEY
        val DLQ_KEY = NotificationStreamPublisher.DLQ_KEY
        val CONSUMER_GROUP = NotificationStreamPublisher.CONSUMER_GROUP
    }
}
