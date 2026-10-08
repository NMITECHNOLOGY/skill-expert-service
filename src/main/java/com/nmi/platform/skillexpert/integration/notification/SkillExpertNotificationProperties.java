/**
 * In-app notification settings for booking updates.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-10-07
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.integration.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Bound from {@code skill-expert.notifications.*}.
 *
 * @param enabled      send booking updates as in-app notifications
 * @param clientId     this service's OAuth client id at auth-service
 * @param clientSecret its secret (environment only); blank turns notifications off
 * @param authBaseUrl  auth-service address for the client_credentials token
 * @param baseUrl      notification-service address; blank = look it up in Eureka by {@code serviceId}
 * @param serviceId    notification-service's Eureka name
 * @param timeZone     zone the visit time is written in, e.g. Asia/Colombo
 */
@ConfigurationProperties(prefix = "skill-expert.notifications")
public record SkillExpertNotificationProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String authBaseUrl,
        String baseUrl,
        String serviceId,
        String timeZone
) {

    public SkillExpertNotificationProperties {
        clientId = clientId == null || clientId.isBlank() ? "skill-expert-service" : clientId;
        serviceId = serviceId == null || serviceId.isBlank() ? "notification-service" : serviceId;
        timeZone = timeZone == null || timeZone.isBlank() ? "Asia/Colombo" : timeZone;
    }

    /**
     * @return {@code true} when notifications are on and a secret is configured
     */
    public boolean configured() {
        return enabled && clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * @return the zone visit times are shown in
     */
    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
