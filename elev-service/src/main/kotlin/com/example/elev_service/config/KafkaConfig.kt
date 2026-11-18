package com.example.elev_service.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import tools.jackson.core.JacksonException

/**
 * Error handling for the Kafka listener.
 *
 * Without this, a message that always fails (a "poison pill", for example
 * broken JSON) would be retried again and again and block the partition.
 *
 * Now:
 *  - temporary errors (e.g. database down for a moment) are retried 3 times, 1 second apart
 *  - broken JSON is not retried, because it will never succeed
 *  - after that the message is sent to the Dead Letter Topic "<topic>.DLT"
 *    and the listener continues with the next message
 */
@Configuration(proxyBeanMethods = false)
class KafkaConfig(
    @Value("\${elev.kafka.topic}") private val topic: String,
) {
    private val log = LoggerFactory.getLogger(KafkaConfig::class.java)

    // Spring creates these topics at startup if they do not exist.
    @Bean
    fun eleverTopic(): NewTopic = TopicBuilder.name(topic).partitions(3).replicas(1).build()

    @Bean
    fun eleverDltTopic(): NewTopic = TopicBuilder.name("$topic.DLT").partitions(1).replicas(1).build()

    // Spring Boot uses this bean as the error handler for all @KafkaListener methods.
    @Bean
    fun kafkaErrorHandler(kafkaTemplate: KafkaTemplate<Any, Any>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, exception ->
            log.error(
                "Sender melding til {}.DLT etter feil: {} (partition {}, offset {})",
                record.topic(), (exception.cause ?: exception).message, record.partition(), record.offset(),
            )
            // Partition -1: let Kafka choose the partition in the DLT.
            TopicPartition("${record.topic()}.DLT", -1)
        }
        return DefaultErrorHandler(recoverer, FixedBackOff(1000L, 3L)).apply {
            addNotRetryableExceptions(JacksonException::class.java)
        }
    }
}
