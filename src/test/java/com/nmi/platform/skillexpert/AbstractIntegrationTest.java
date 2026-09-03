/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base integration test configuration using Testcontainers for PostgreSQL and Redis.
 *
 * <p>Business tests are intentionally not generated in the foundation. Extend this
 * class when adding feature-level integration tests.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("skill_expert")
            .withUsername("skill_expert")
            .withPassword("skill_expert");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    /**
     * Registers dynamic datasource, Redis, JWT issuer, and JPA properties from Testcontainers.
     *
     * @param registry Spring dynamic property registry to populate
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:8443");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}
