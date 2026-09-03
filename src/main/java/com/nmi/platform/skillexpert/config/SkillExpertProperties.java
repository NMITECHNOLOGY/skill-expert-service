/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Top-level Skill Expert Service properties.
 *
 * <p>Bound from {@code nmi.skill-expert}. CORS, security, and downstream URLs
 * use their own {@code @ConfigurationProperties} types.
 *
 * @param applicationName logical application name
 * @param apiBasePath base path for Skill Expert APIs
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@ConfigurationProperties(prefix = "nmi.skill-expert")
public record SkillExpertProperties(
        String applicationName,
        String apiBasePath
) {
}
