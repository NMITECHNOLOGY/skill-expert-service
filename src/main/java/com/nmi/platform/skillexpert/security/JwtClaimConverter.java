package com.nmi.platform.skillexpert.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtClaimConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        appendClaim(jwt.getClaim("permissions"), authorities, false);
        appendClaim(jwt.getClaim("roles"), authorities, true);
        return authorities;
    }

    private void appendClaim(Object claimValue, List<GrantedAuthority> authorities, boolean asRole) {
        if (claimValue == null) {
            return;
        }
        List<String> values;
        if (claimValue instanceof Collection<?> collection) {
            values = collection.stream().map(Object::toString).toList();
        } else if (claimValue instanceof String value && value.contains(",")) {
            values = List.of(value.split(","));
        } else {
            values = List.of(claimValue.toString());
        }
        for (String raw : values) {
            String normalized = raw.trim().toUpperCase();
            if (normalized.isEmpty()) {
                continue;
            }
            if (asRole && !normalized.startsWith("ROLE_")) {
                normalized = "ROLE_" + normalized;
            }
            if (!asRole && normalized.startsWith("ROLE_")) {
                continue;
            }
            authorities.add(new SimpleGrantedAuthority(normalized));
        }
    }
}
