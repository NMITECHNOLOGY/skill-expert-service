package com.nmi.platform.skillexpert.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class KycVerificationSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/skill-experts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browseDoesNotRequireKyc() throws Exception {
        mockMvc.perform(get("/api/v1/skill-experts")
                        .with(jwt().jwt(j -> j.claim(KycVerificationFilter.KYC_CLAIM, "PENDING"))))
                .andExpect(status().isOk());
    }

    @Test
    void meProfileRequiresVerifiedKyc() throws Exception {
        mockMvc.perform(get("/api/v1/skill-experts/me/profile")
                        .with(jwt().jwt(j -> j
                                .claim(KycVerificationFilter.KYC_CLAIM, "PENDING")
                                .claim("user_id", "u-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KYC_NOT_VERIFIED"));
    }

    @Test
    void verifiedTokenCanLoadOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/skill-experts/me/profile")
                        .with(jwt().jwt(j -> j
                                .claim(KycVerificationFilter.KYC_CLAIM, "VERIFIED")
                                .claim("user_id", "u-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
    }

    @Test
    void livenessProbeIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }
}
