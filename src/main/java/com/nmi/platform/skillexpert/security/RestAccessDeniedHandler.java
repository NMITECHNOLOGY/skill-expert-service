/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.security;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns RFC 7807 Problem Details for forbidden requests.
 *
 * <p>Prefer {@code platform-common} Problem Details helpers when available.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final JsonMapper jsonMapper;

    /**
     * Creates an access denied handler that serializes Problem Details as JSON.
     *
     * @param jsonMapper Jackson JSON mapper used to write the response body
     */
    public RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Writes a 403 Problem Details response when access is denied.
     *
     * @param request HTTP request that was denied
     * @param response HTTP response to write
     * @param accessDeniedException exception describing the access denial
     * @throws IOException if writing the response body fails
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        log.debug("Access denied to {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource");
        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://nmi.platform/problems/forbidden"));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("path", request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.putAll(problem.getProperties() == null ? Map.of() : problem.getProperties());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
