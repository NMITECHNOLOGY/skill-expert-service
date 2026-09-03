/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Skill Expert Service - domain microservice for the Skill Expert mini app.
 *
 * <p>This service owns Skill Expert domain APIs and persistence. Authentication,
 * loyalty wallets, and payment capture remain in Auth, Loyalty, and Payment
 * services; this application must not re-implement them.
 *
 * <p>Shared OpenAPI, Redis cache, Feign interceptors, and correlation IDs come
 * from {@code platform-common} auto-configuration.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@SpringBootApplication(scanBasePackages = {
        "com.nmi.platform.skillexpert",
        "com.nmi.platform.common"
})
@ConfigurationPropertiesScan(basePackages = "com.nmi.platform.skillexpert")
public class SkillExpertServiceApplication {

    /**
     * Boots the Skill Expert Service Spring application.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(SkillExpertServiceApplication.class, args);
    }
}
