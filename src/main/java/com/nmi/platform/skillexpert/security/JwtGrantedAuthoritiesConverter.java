/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps JWT permission / role claims to Spring Security authorities.
 *
 * <p>Does not implement business permission checks - only claim extraction.
 * Prefer {@code platform-common} JWT utilities when a shared converter exists.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@Component
public class JwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final SecurityProperties securityProperties;

    /**
     * Creates a converter using security property claim names and authority prefixes.
     *
     * @param securityProperties security configuration for claim names and prefixes
     */
    public JwtGrantedAuthoritiesConverter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * Converts JWT authority and role claims into Spring Security granted authorities.
     *
     * @param jwt source JWT containing permission and role claims
     * @return immutable collection of granted authorities derived from the JWT
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        Object claim = jwt.getClaim(securityProperties.authoritiesClaim());
        if (claim instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value != null) {
                    authorities.add(new SimpleGrantedAuthority(
                            securityProperties.authorityPrefix() + value.toString()));
                }
            }
        } else if (claim instanceof String value && !value.isBlank()) {
            for (String part : value.split("[,\\s]+")) {
                if (!part.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(
                            securityProperties.authorityPrefix() + part.trim()));
                }
            }
        }

        Object roles = jwt.getClaim("roles");
        if (roles instanceof Collection<?> roleCollection) {
            for (Object role : roleCollection) {
                if (role != null) {
                    String roleName = role.toString();
                    if (!roleName.startsWith("ROLE_")) {
                        roleName = "ROLE_" + roleName;
                    }
                    authorities.add(new SimpleGrantedAuthority(roleName));
                }
            }
        }

        return List.copyOf(authorities);
    }
}
