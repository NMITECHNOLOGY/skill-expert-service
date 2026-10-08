/**
 * HTTP client for notification-service's in-app endpoint.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-10-07
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.integration.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Posts in-app notifications as this service. The address is {@code skill-expert.notifications.base-url}
 * when set (dev, direct call), otherwise notification-service is looked up in Eureka (local stack).
 */
@Component
public class NotificationClient {

    static final String IN_APP_PATH = "/api/v1/notifications/in-app";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final SkillExpertNotificationProperties properties;
    private final ServiceTokenProvider tokens;
    private final ObjectProvider<LoadBalancerClient> loadBalancer;
    private final RestClient restClient;

    @Autowired
    public NotificationClient(
            SkillExpertNotificationProperties properties,
            ServiceTokenProvider tokens,
            ObjectProvider<LoadBalancerClient> loadBalancer) {
        this(properties, tokens, loadBalancer, defaultRestClient());
    }

    NotificationClient(
            SkillExpertNotificationProperties properties,
            ServiceTokenProvider tokens,
            ObjectProvider<LoadBalancerClient> loadBalancer,
            RestClient restClient) {
        this.properties = properties;
        this.tokens = tokens;
        this.loadBalancer = loadBalancer;
        this.restClient = restClient;
    }

    /**
     * @param idempotencyKey stable per booking event, so a retry never duplicates the notification
     * @param request        notification body
     * @throws IllegalStateException when no token or no notification-service address is available
     */
    public void createInApp(String idempotencyKey, InAppNotificationRequest request) {
        String token = tokens.token()
                .orElseThrow(() -> new IllegalStateException("no service token"));
        URI target = baseUri().resolve(IN_APP_PATH);
        restClient.post()
                .uri(target)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    URI baseUri() {
        String configured = properties.baseUrl();
        if (configured != null && !configured.isBlank()) {
            return URI.create(configured);
        }
        LoadBalancerClient client = loadBalancer.getIfAvailable();
        ServiceInstance instance = client == null ? null : client.choose(properties.serviceId());
        if (instance == null) {
            throw new IllegalStateException(properties.serviceId() + " is not registered in Eureka");
        }
        return instance.getUri();
    }

    private static RestClient defaultRestClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory).build();
    }
}
