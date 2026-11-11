package com.example.elev_api.service

import com.example.elev_api.client.ElevServiceClient
import com.example.elev_api.kafka.ElevProducer
import com.example.elev_api.model.Elev
import com.example.elev_api.model.OpprettElevRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ElevService(
    private val elevProducer: ElevProducer,
    private val elevServiceClient: ElevServiceClient,
) {

    fun opprett(request: OpprettElevRequest): Elev {
        val elev = Elev(
            id = UUID.randomUUID(),
            navn = request.navn.trim(),
            klasse = request.klasse.trim(),
        )
        elevProducer.send(elev)
        return elev
    }

    fun hent(id: UUID): Elev? = elevServiceClient.hent(id)

    fun hentAlle(): List<Elev> = elevServiceClient.hentAlle()
}