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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns RFC 7807 Problem Details for unauthenticated requests.
 *
 * <p>Prefer {@code platform-common} Problem Details helpers when available.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final JsonMapper jsonMapper;

    /**
     * Creates an authentication entry point that serializes Problem Details as JSON.
     *
     * @param jsonMapper Jackson JSON mapper used to write the response body
     */
    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Writes a 401 Problem Details response when authentication is required but missing or invalid.
     *
     * @param request HTTP request that triggered authentication failure
     * @param response HTTP response to write
     * @param authException exception describing the authentication failure
     * @throws IOException if writing the response body fails
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        log.debug("Unauthorized access to {}: {}", request.getRequestURI(), authException.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Full authentication is required to access this resource");
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("https://nmi.platform/problems/unauthorized"));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("path", request.getRequestURI());

        writeProblem(response, HttpStatus.UNAUTHORIZED, problem);
    }

    /**
     * Serializes a Problem Detail payload to the HTTP response as application/problem+json.
     *
     * @param response HTTP response to write
     * @param status HTTP status to set on the response
     * @param problem Problem Detail model to serialize
     * @throws IOException if writing the response body fails
     */
    private void writeProblem(HttpServletResponse response, HttpStatus status, ProblemDetail problem)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance());
        }
        body.putAll(problem.getProperties() == null ? Map.of() : problem.getProperties());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
