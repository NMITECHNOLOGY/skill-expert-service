/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * OAuth2 Resource Server security filter chain foundation.
 *
 * <p>Permission-based authorization annotations are enabled; concrete permission
 * implementations are deferred to feature work. Prefer JWT claim utilities from
 * {@code platform-common} when wiring authorities.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    /**
     * Creates security configuration with handlers, CORS, and JWT authority conversion.
     *
     * @param securityProperties security properties including permit-all patterns and claim names
     * @param authenticationEntryPoint entry point for unauthenticated requests
     * @param accessDeniedHandler handler for forbidden requests
     * @param corsConfigurationSource CORS configuration source
     * @param jwtGrantedAuthoritiesConverter converter from JWT claims to authorities
     */
    public SecurityConfig(
            SecurityProperties securityProperties,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource,
            JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter) {
        this.securityProperties = securityProperties;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtGrantedAuthoritiesConverter = jwtGrantedAuthoritiesConverter;
    }

    /**
     * Builds the primary security filter chain for the OAuth2 resource server.
     *
     * @param http HTTP security builder to configure
     * @return configured security filter chain
     * @throws Exception if security configuration fails
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(securityProperties.permitAllPatterns().toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Creates a JWT authentication converter using configured principal and authority claims.
     *
     * @return converter from {@link Jwt} to an authentication token
     */
    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        converter.setPrincipalClaimName(securityProperties.principalClaim());
        return converter;
    }
}
