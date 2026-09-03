/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URLs for downstream platform services used by Feign clients.
 *
 * <p>Bound from {@code nmi.skill-expert.downstream}. Provides typed lookup of
 * service endpoints by configuration key.
 *
 * @param services map of service keys to endpoint settings
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@ConfigurationProperties(prefix = "nmi.skill-expert.downstream")
public record DownstreamServicesProperties(Map<String, ServiceEndpoint> services) {

    /**
     * Normalizes the services map to an empty immutable map when null,
     * otherwise an immutable copy.
     */
    public DownstreamServicesProperties {
        services = services == null ? Map.of() : Map.copyOf(services);
    }

    /**
     * Returns a configured endpoint for the given key.
     *
     * @param key downstream service configuration key
     * @return the matching {@link ServiceEndpoint}
     * @throws IllegalStateException if the key is missing or its base URL is blank
     */
    public ServiceEndpoint require(String key) {
        ServiceEndpoint endpoint = services.get(key);
        if (endpoint == null || endpoint.baseUrl() == null || endpoint.baseUrl().isBlank()) {
            throw new IllegalStateException("Missing downstream service configuration for key: " + key);
        }
        return endpoint;
    }

    /**
     * Connection settings for a single downstream service.
     *
     * @param baseUrl base URL of the downstream service
     * @param connectTimeoutMs optional connect timeout in milliseconds
     * @param readTimeoutMs optional read timeout in milliseconds
     * @author Viraj Sachin
 * @since 2026-09-03
     */
    public record ServiceEndpoint(String baseUrl, Integer connectTimeoutMs, Integer readTimeoutMs) {
    }
}
