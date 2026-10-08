/**
 * client_credentials access token for calls this service makes as itself.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-10-07
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.integration.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches a token from auth-service's {@code /oauth2/token} with this service's client id and
 * secret, and reuses it until shortly before it expires. A failure yields an empty token: callers
 * treat the call as skipped rather than failing their own operation.
 */
@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final SkillExpertNotificationProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    @Autowired
    public ServiceTokenProvider(SkillExpertNotificationProperties properties) {
        this(properties, restClient(properties), Clock.systemUTC());
    }

    ServiceTokenProvider(SkillExpertNotificationProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    /**
     * @return a valid access token, or empty when not configured or auth-service is unreachable
     */
    public synchronized Optional<String> token() {
        if (!properties.configured()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedUntil)) {
            return Optional.of(cachedToken);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/oauth2/token")
                    .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object token = response == null ? null : response.get("access_token");
            if (token == null) {
                log.warn("auth-service returned no access_token for {}", properties.clientId());
                return Optional.empty();
            }
            long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
            cachedToken = token.toString();
            cachedUntil = now.plusSeconds(expiresIn).minus(EXPIRY_SKEW);
            return Optional.of(cachedToken);
        } catch (RestClientException ex) {
            log.warn("Service token for {} unavailable: {}", properties.clientId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private static RestClient restClient(SkillExpertNotificationProperties properties) {
        String baseUrl = properties.authBaseUrl() == null ? "" : properties.authBaseUrl();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
