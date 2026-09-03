/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA foundation for Skill Expert-owned persistence.
 *
 * <p>Auditing timestamps / actors should align with {@code platform-common}
 * {@code BaseEntity} conventions when entities are introduced.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing
@EntityScan(basePackages = "com.nmi.platform.skillexpert.model.entity")
@EnableJpaRepositories(basePackages = "com.nmi.platform.skillexpert.repository")
public class JpaConfig {
}
