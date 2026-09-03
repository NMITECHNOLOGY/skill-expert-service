/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security-related configuration properties.
 *
 * <p>Bound from {@code nmi.skill-expert.security}, covering JWT claim mapping
 * and request patterns that do not require authentication.
 *
 * @param principalClaim JWT claim used as the authentication principal
 * @param authoritiesClaim JWT claim containing permission authorities
 * @param authorityPrefix prefix applied to extracted authority values
 * @param permitAllPatterns ant-style path patterns that are publicly accessible
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@ConfigurationProperties(prefix = "nmi.skill-expert.security")
public record SecurityProperties(
        String principalClaim,
        String authoritiesClaim,
        String authorityPrefix,
        List<String> permitAllPatterns
) {

    /**
     * Applies defaults for blank claim names and a standard permit-all pattern list when unset.
     */
    public SecurityProperties {
        principalClaim = principalClaim == null || principalClaim.isBlank() ? "sub" : principalClaim;
        authoritiesClaim = authoritiesClaim == null || authoritiesClaim.isBlank() ? "permissions" : authoritiesClaim;
        authorityPrefix = authorityPrefix == null ? "" : authorityPrefix;
        permitAllPatterns = permitAllPatterns == null
                ? List.of(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/actuator/prometheus",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html")
                : List.copyOf(permitAllPatterns);
    }
}
