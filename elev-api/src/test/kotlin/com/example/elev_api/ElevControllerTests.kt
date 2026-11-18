package com.example.elev_api

import com.example.elev_api.client.ElevServiceClient
import com.example.elev_api.controller.ElevController
import com.example.elev_api.kafka.ElevProducer
import com.example.elev_api.model.Elev
import com.example.elev_api.service.ElevService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.client.ResourceAccessException
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Web layer tests for elev-api.
 *
 * Only the controller and ElevService are real. Kafka (ElevProducer) and the
 * HTTP call to elev-service (ElevServiceClient) are replaced with Mockito mocks,
 * so the tests need neither Kafka nor elev-service to be running.
 */
@WebMvcTest(ElevController::class)
@Import(ElevService::class)
class ElevControllerTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var elevProducer: ElevProducer

    @MockitoBean
    lateinit var elevServiceClient: ElevServiceClient

    // Mockito matchers (any(), capture()) return null, but Kotlin checks that a
    // non-null parameter never gets null. These helpers register the matcher
    // and hide the null behind a generic type, so Kotlin does not check it.
    @Suppress("UNCHECKED_CAST")
    private fun <T> nullAs(): T = null as T

    private fun anyElev(): Elev {
        Mockito.any(Elev::class.java)
        return nullAs()
    }

    private fun ArgumentCaptor<Elev>.captureElev(): Elev {
        capture()
        return nullAs()
    }

    @Test
    fun `POST with valid body returns 202 and sends the student to Kafka`() {
        mockMvc.post("/elever") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"navn":"  Ola Nordmann ","klasse":"10A"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.id") { exists() }
            jsonPath("$.navn") { value("Ola Nordmann") }
            jsonPath("$.klasse") { value("10A") }
        }

        val sent = ArgumentCaptor.forClass(Elev::class.java)
        verify(elevProducer).send(sent.captureElev())
        assertEquals("Ola Nordmann", sent.value.navn) // trimmed
        assertEquals("10A", sent.value.klasse)
    }

    @Test
    fun `POST with blank navn returns 400 and sends nothing`() {
        mockMvc.post("/elever") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"navn":"   ","klasse":"10A"}"""
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(elevProducer)
    }

    @Test
    fun `POST returns 503 when Kafka fails`() {
        doThrow(RuntimeException("Kafka is down")).`when`(elevProducer).send(anyElev())

        mockMvc.post("/elever") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"navn":"Kari","klasse":"9B"}"""
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.feil") { value("Kunne ikke sende eleven til Kafka") }
        }
    }

    @Test
    fun `GET elever returns the list from elev-service`() {
        val elev = Elev(UUID.randomUUID(), "Ola Nordmann", "10A")
        `when`(elevServiceClient.hentAlle()).thenReturn(listOf(elev))

        mockMvc.get("/elever").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(elev.id.toString()) }
            jsonPath("$[0].navn") { value("Ola Nordmann") }
        }
    }

    @Test
    fun `GET elever by id returns 200 when the student exists`() {
        val elev = Elev(UUID.randomUUID(), "Kari", "9B")
        `when`(elevServiceClient.hent(elev.id)).thenReturn(elev)

        mockMvc.get("/elever/${elev.id}").andExpect {
            status { isOk() }
            jsonPath("$.klasse") { value("9B") }
        }
    }

    @Test
    fun `GET elever by id returns 404 when elev-service does not know it`() {
        val id = UUID.randomUUID()
        `when`(elevServiceClient.hent(id)).thenReturn(null)

        mockMvc.get("/elever/$id").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET elever returns 503 when elev-service is down`() {
        `when`(elevServiceClient.hentAlle()).thenThrow(ResourceAccessException("Connection refused"))

        mockMvc.get("/elever").andExpect {
            status { isServiceUnavailable() }
        }
    }

    @Test
    fun `GET elever by id returns 503 when elev-service is down`() {
        val id = UUID.randomUUID()
        `when`(elevServiceClient.hent(id)).thenThrow(ResourceAccessException("Connection refused"))

        mockMvc.get("/elever/$id").andExpect {
            status { isServiceUnavailable() }
        }
    }
}
