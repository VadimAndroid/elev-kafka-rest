package com.example.elev_service.repository

import com.example.elev_service.model.Elev
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


@Repository
class ElevRepository {
    private val elever = ConcurrentHashMap<UUID, Elev>()

    fun lagre(elev: Elev): Elev {
        elever[elev.id] = elev
        return elev
    }
    fun finn(id: UUID): Elev? = elever[id]

    fun  finnAlle(): List<Elev> = elever.values.toList()
}