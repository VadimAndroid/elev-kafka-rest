package com.example.elev_service

import com.example.elev_service.consumer.ElevConsumer
import com.example.elev_service.repository.ElevRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals

/**
 * Testene bruker en egen H2-database i minnet, ikke den ekte filen i data/.
 * Kafka-lytteren startes ikke, så testene leser ikke meldinger fra den ekte consumer groupen.
 */
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.kafka.listener.auto-startup=false",
    ],
)
@AutoConfigureMockMvc
class ElevServiceApplicationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var elevRepository: ElevRepository

    @Autowired
    lateinit var elevConsumer: ElevConsumer

    @BeforeEach
    fun tomDatabase() {
        elevRepository.deleteAll()
    }

    @Test
    fun contextLoads() {
    }

    @Test
    fun `GET elever gir tom liste naar databasen er tom`() {
        mockMvc.get("/elever").andExpect {
            status { isOk() }
            content { json("[]") }
        }
    }

    @Test
    fun `GET elev som ikke finnes gir 404`() {
        mockMvc.get("/elever/00000000-0000-0000-0000-000000000000").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `samme melding to ganger gir bare en rad`() {
        val melding = """{"id":"11111111-1111-1111-1111-111111111111","navn":"Duplikat Test","klasse":"9C"}"""

        elevConsumer.motta(melding)
        elevConsumer.motta(melding)

        assertEquals(1, elevRepository.count())
        mockMvc.get("/elever/11111111-1111-1111-1111-111111111111").andExpect {
            status { isOk() }
            jsonPath("$.navn") { value("Duplikat Test") }
            jsonPath("$.klasse") { value("9C") }
        }
    }
}
