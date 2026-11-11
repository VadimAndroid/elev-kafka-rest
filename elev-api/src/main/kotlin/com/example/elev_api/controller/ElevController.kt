package com.example.elev_api.controller

import com.example.elev_api.model.Elev
import com.example.elev_api.model.OpprettElevRequest
import com.example.elev_api.service.ElevService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.ResourceAccessException
import java.util.UUID

@RestController
@RequestMapping("/elever")
class ElevController(private val elevService: ElevService) {

    @PostMapping
    fun opprett(@Valid @RequestBody request: OpprettElevRequest): ResponseEntity<*> =
        try {
            val elev = elevService.opprett(request)
            ResponseEntity.accepted().body(elev)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("feil" to "Kunne ikke sende eleven til Kafka"))
        }

    @GetMapping("/{id}")
    fun hentEn(@PathVariable id: UUID): ResponseEntity<Elev> =
        try {
            val elev = elevService.hent(id)
            if (elev != null) ResponseEntity.ok(elev) else ResponseEntity.notFound().build()
        } catch (e: ResourceAccessException) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

    @GetMapping
    fun hentAlle(): ResponseEntity<List<Elev>> =
        try {
            ResponseEntity.ok(elevService.hentAlle())
        } catch (e: ResourceAccessException) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
}