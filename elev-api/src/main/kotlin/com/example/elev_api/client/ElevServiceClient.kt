package com.example.elev_api.client

import com.example.elev_api.model.Elev
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Component
class ElevServiceClient(
    private val jsonMapper: JsonMapper,
    @Value("\${elev.service.url}") baseUrl: String,
) {
    private val restClient = RestClient.create(baseUrl)

    fun hentAlle(): List<Elev> {
        val json = restClient.get()
            .uri("/elever")
            .retrieve()
            .body(String::class.java) ?: return emptyList()
        return jsonMapper.readValue(json, Array<Elev>::class.java).toList()
    }

    fun hent(id: UUID): Elev? {
        return try {
            val json = restClient.get()
                .uri("/elever/{id}", id)
                .retrieve()
                .body(String::class.java) ?: return null
            jsonMapper.readValue(json, Elev::class.java)
        } catch (e: HttpClientErrorException.NotFound) {
            null
        }
    }
}