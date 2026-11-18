package com.example.elev_service.repository

import com.example.elev_service.model.ElevEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA lager implementasjonen automatisk ved oppstart.
 * Vi får blant annet save(), findById() og findAll() uten å skrive kode.
 */
interface ElevRepository : JpaRepository<ElevEntity, UUID>
