package com.nmi.platform.skillexpert.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CurrentUser {

    public static final String USER_ID_CLAIM = "user_id";

    public String requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated JWT required");
        }
        String userId = jwt.getClaimAsString(USER_ID_CLAIM);
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getSubject();
        }
        if (!StringUtils.hasText(userId)) {
            throw new IllegalStateException("JWT is missing user_id / sub");
        }
        return userId.trim();
    }

    /**
     * Every identity on the token. A profile may have been stored under {@code user_id}
     * while a later call only matches {@code sub}, or the other way around.
     */
    public List<String> identityKeys() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return List.of(requireUserId());
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, jwt.getClaimAsString(USER_ID_CLAIM));
        addKey(keys, jwt.getSubject());
        if (keys.isEmpty()) {
            throw new IllegalStateException("JWT is missing user_id / sub");
        }
        return new ArrayList<>(keys);
    }

    public String optionalUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        return jwt.getSubject();
    }

    public String displayName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return requireUserId();
        }
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        String full = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        if (StringUtils.hasText(full)) {
            return full;
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        String name = jwt.getClaimAsString("name");
        if (StringUtils.hasText(name)) {
            return name.trim();
        }
        return requireUserId();
    }

    private static void addKey(LinkedHashSet<String> keys, String value) {
        if (StringUtils.hasText(value)) {
            keys.add(value.trim());
        }
    }
}
