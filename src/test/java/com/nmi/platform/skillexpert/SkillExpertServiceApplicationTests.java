/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Lightweight foundation smoke tests. Full Spring context / Testcontainers coverage
 * belongs in subclasses of {@link AbstractIntegrationTest} once features are added.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
class SkillExpertServiceApplicationTests {

    /**
     * Verifies the application entrypoint class is present under the expected package name.
     */
    @Test
    void applicationEntrypointIsDefined() {
        assertNotNull(SkillExpertServiceApplication.class);
        assertEquals("com.nmi.platform.skillexpert.SkillExpertServiceApplication",
                SkillExpertServiceApplication.class.getName());
    }
}
