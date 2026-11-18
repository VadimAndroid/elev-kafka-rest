package com.example.elev_service

import com.example.elev_service.repository.ElevRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests against a real PostgreSQL and a real Kafka (Testcontainers).
 * The flow is the same as in production: Kafka message -> ElevConsumer -> PostgreSQL -> GET.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
class ElevServiceApplicationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var elevRepository: ElevRepository

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var consumerFactory: ConsumerFactory<String, String>

    @Value("\${elev.kafka.topic}")
    lateinit var topic: String

    @BeforeEach
    fun emptyDatabase() {
        elevRepository.deleteAll()
    }

    private fun melding(id: UUID, navn: String, klasse: String) =
        """{"id":"$id","navn":"$navn","klasse":"$klasse"}"""

    private fun send(id: UUID, json: String) {
        kafkaTemplate.send(topic, id.toString(), json).get()
    }

    @Test
    fun `GET elever returns an empty list when the database is empty`() {
        mockMvc.get("/elever").andExpect {
            status { isOk() }
            content { json("[]") }
        }
    }

    @Test
    fun `GET elever with unknown id returns 404`() {
        mockMvc.get("/elever/${UUID.randomUUID()}").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `a Kafka message is stored in PostgreSQL and returned by GET`() {
        val id = UUID.randomUUID()

        send(id, melding(id, "Ola Nordmann", "10A"))

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertNotNull(elevRepository.findByIdOrNull(id))
        }
        mockMvc.get("/elever/$id").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
            jsonPath("$.navn") { value("Ola Nordmann") }
            jsonPath("$.klasse") { value("10A") }
        }
    }

    @Test
    fun `the same Kafka message twice creates only one row`() {
        val id = UUID.randomUUID()
        val markerId = UUID.randomUUID()

        send(id, melding(id, "Duplikat Test", "9C"))
        send(id, melding(id, "Duplikat Test", "9C"))
        // Same key -> same partition -> processed after the two duplicates.
        // When the marker is stored, both duplicates have been handled.
        kafkaTemplate.send(topic, id.toString(), melding(markerId, "Marker", "0X")).get()

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertNotNull(elevRepository.findByIdOrNull(markerId))
        }
        assertEquals(2, elevRepository.count())
        mockMvc.get("/elever").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `a broken message goes to the Dead Letter Topic and does not block the next one`() {
        val brokenKey = UUID.randomUUID()
        val id = UUID.randomUUID()

        send(brokenKey, "dette er ikke JSON")
        send(id, melding(id, "Etter feilen", "8B"))

        // The valid message after the broken one is still stored.
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertNotNull(elevRepository.findByIdOrNull(id))
        }

        // The broken message is in "elever.DLT", unchanged.
        consumerFactory.createConsumer("dlt-test", null).use { consumer ->
            consumer.subscribe(listOf("$topic.DLT"))
            val record = KafkaTestUtils.getSingleRecord(consumer, "$topic.DLT", Duration.ofSeconds(30))
            assertEquals(brokenKey.toString(), record.key())
            assertEquals("dette er ikke JSON", record.value())
        }
    }
}
