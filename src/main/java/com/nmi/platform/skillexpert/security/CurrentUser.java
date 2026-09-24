package com.nmi.platform.skillexpert.security;

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
}
