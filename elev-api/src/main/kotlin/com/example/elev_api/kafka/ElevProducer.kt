package com.example.elev_api.kafka

import com.example.elev_api.model.Elev
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.TimeUnit

@Component
class ElevProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val jsonMapper: JsonMapper,
    @Value("\${elev.kafka.topic}") private val topic: String,
) {
    private val log = LoggerFactory.getLogger(ElevProducer::class.java)

    fun send(elev: Elev) {
        val json = jsonMapper.writeValueAsString(elev)
        kafkaTemplate.send(topic, elev.id.toString(), json).get(10, TimeUnit.SECONDS)
        log.info("Sendte elev med id {} til Kafka", elev.id)
    }
}