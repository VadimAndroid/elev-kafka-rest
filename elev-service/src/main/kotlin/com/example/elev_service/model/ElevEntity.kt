package com.example.elev_service.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Databasemodell for en elev. Én instans = én rad i tabellen "elev".
 *
 * ID-en kommer fra Kafka-meldingen, derfor er det ingen @GeneratedValue:
 * databasen skal ikke lage en ny ID.
 */
@Entity
@Table(name = "elev")
class ElevEntity(
    @Id
    @Column(nullable = false, updatable = false)
    var id: UUID,

    @Column(nullable = false)
    var navn: String,

    @Column(nullable = false)
    var klasse: String,
) {
    /** Gjør om til samme format som API-et alltid har returnert. */
    fun tilElev(): Elev = Elev(id = id, navn = navn, klasse = klasse)

    companion object {
        fun fra(elev: Elev): ElevEntity = ElevEntity(id = elev.id, navn = elev.navn, klasse = elev.klasse)
    }
}
