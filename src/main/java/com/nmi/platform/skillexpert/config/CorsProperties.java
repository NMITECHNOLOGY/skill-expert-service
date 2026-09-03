/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings bound from {@code nmi.skill-expert.cors}.
 *
 * @param allowedOrigins allowed origin patterns or exact origins
 * @param allowedMethods allowed HTTP methods
 * @param allowedHeaders allowed request headers
 * @param exposedHeaders response headers exposed to the browser
 * @param allowCredentials whether credentials are allowed in CORS requests
 * @param maxAge preflight cache duration in seconds
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@ConfigurationProperties(prefix = "nmi.skill-expert.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAge
) {

    /**
     * Applies defaults and defensive copies for list fields when values are null.
     */
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? new ArrayList<>() : List.copyOf(allowedOrigins);
        allowedMethods = allowedMethods == null ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : List.copyOf(allowedMethods);
        allowedHeaders = allowedHeaders == null ? List.of("*") : List.copyOf(allowedHeaders);
        exposedHeaders = exposedHeaders == null ? List.of("Authorization", "X-Correlation-Id", "X-Request-Id")
                : List.copyOf(exposedHeaders);
    }
}
