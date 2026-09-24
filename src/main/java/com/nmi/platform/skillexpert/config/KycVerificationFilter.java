package com.nmi.platform.skillexpert.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects authenticated callers whose identity has not completed KYC.
 *
 * <p>Runs after {@code BearerTokenAuthenticationFilter} so the principal is a
 * validated {@link Jwt}. Unauthenticated requests are left alone: the
 * authorization rules in {@link SecurityConfig} produce the 401 for those.
 *
 * <p>Deliberately <em>not</em> a {@code @Component}: Boot would then register
 * it a second time as a plain servlet filter (outside the security chain,
 * where no principal exists yet). It is instantiated once by
 * {@link SecurityConfig} instead.
 */
public class KycVerificationFilter extends OncePerRequestFilter {

    /** Claim emitted by auth-service; compared case-insensitively. */
    static final String KYC_CLAIM = "kyc_status";
    static final String KYC_VERIFIED = "VERIFIED";

    private static final Logger log = LoggerFactory.getLogger(KycVerificationFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Probes and metrics never carry a user token.
        if (path.startsWith("/actuator")) {
            return true;
        }
        // Control-panel admins are not KYC-verified experts.
        if (path.contains("/api/v1/skill-experts/admin/")) {
            return true;
        }
        // Customer browse of approved experts — any authenticated member.
        // Only /me/** (draft/submit) requires KYC VERIFIED.
        return path.startsWith("/api/v1/skill-experts")
                && !path.contains("/me/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String kycStatus = jwt.getClaimAsString(KYC_CLAIM);
            if (!KYC_VERIFIED.equalsIgnoreCase(kycStatus)) {
                log.debug("Rejecting subject {} with kyc_status={}", jwt.getSubject(), kycStatus);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        "{\"error\":\"Forbidden\",\"code\":\"KYC_NOT_VERIFIED\","
                        + "\"message\":\"Complete identity verification to access skill expert features.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
