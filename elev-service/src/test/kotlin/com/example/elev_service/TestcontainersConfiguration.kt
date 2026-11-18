package com.example.elev_service

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts a real PostgreSQL and a real Kafka in Docker for the tests.
 *
 * @ServiceConnection tells Spring Boot to read host, port, user and password
 * from the running container, so spring.datasource.* and
 * spring.kafka.bootstrap-servers point to the containers automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))

    @Bean
    @ServiceConnection
    fun kafka(): KafkaContainer =
        KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"))
}
