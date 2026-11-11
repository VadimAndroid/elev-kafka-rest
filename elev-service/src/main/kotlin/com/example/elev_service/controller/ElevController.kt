package com.example.elev_service.controller

import com.example.elev_service.model.Elev
import com.example.elev_service.repository.ElevRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/elever")
class ElevController(private val elevRepository: ElevRepository) {

    @GetMapping("/{id}")
    fun hentEn(@PathVariable id: UUID): ResponseEntity<Elev> {
        val elev = elevRepository.finn(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(elev)
    }

    @GetMapping
    fun hentAlle(): List<Elev> = elevRepository.finnAlle()
}