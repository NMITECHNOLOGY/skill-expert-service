/**
 * Author: Viraj Sachin
 * Created: 2026-09-03
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Feign clients in this service's {@code integration} packages.
 *
 * <p>Do not add interceptors, error decoders, retry, or loggers here.
 * Those come from {@code platform-common} {@code FeignClientConfig}.
 *
 * @author Viraj Sachin
 * @since 2026-09-03
 */
@Configuration
@EnableFeignClients(basePackages = "com.nmi.platform.skillexpert.integration")
public class OpenFeignConfig {
}
