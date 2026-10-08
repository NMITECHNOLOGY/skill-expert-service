/**
 * In-app notification request sent to notification-service.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-10-07
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.integration.notification;

import java.util.Map;

/**
 * Body of {@code POST /api/v1/notifications/in-app}.
 *
 * @param userId     recipient public user id (customer or expert)
 * @param type       MINI_APP_EVENT
 * @param title      title
 * @param body       body
 * @param attributes non-sensitive details (booking id, service)
 * @param deepLink   where the app opens on tap
 * @param source     always {@code skill-expert} for this service
 */
public record InAppNotificationRequest(
        String userId,
        String type,
        String title,
        String body,
        Map<String, Object> attributes,
        String deepLink,
        String source
) {
}
