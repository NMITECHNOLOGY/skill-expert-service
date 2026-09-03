<!--
  Author: Viraj Sachin
  Created: 2026-09-03
  Copyright (c) 2026 NMI Infra Pvt Ltd
-->
# Package Structure

Base package: `com.nmi.platform.skillexpert`

## Current (foundation)

```
com.nmi.platform.skillexpert
├── SkillExpertServiceApplication
├── config/
│   ├── SkillExpertProperties
│   ├── CorsProperties / CorsConfig
│   ├── DownstreamServicesProperties
│   ├── OpenFeignConfig      # @EnableFeignClients only
│   └── JpaConfig            # entity / repository scan packages
└── security/               # OAuth2 resource server
```

## Add with features

Create these packages when you add real classes (same as payment-service / loyalty-service — no `package-info.java` placeholders):

```
controller/
service/
repository/
model/entity|dto|request|response|mapper|enums|constants|projection
integration/authorization|loyalty|payment|notification
exception/
```

## From `platform-common` (do not duplicate)

Auto-configured when this service starts:

| Concern | Source |
|---|---|
| OpenAPI / Swagger JWT scheme | `OpenApiConfig` |
| Redis `CacheManager` + `@EnableCaching` | `RedisCacheConfig` |
| Feign interceptors, error decoder, retry, logger | `FeignClientConfig` |
| Correlation ID filter | `PlatformCommonAutoConfiguration` |

Also reuse `ApiResponse`, `BaseEntity`, exception types, JWT helpers, and shared constants.

## Resources (classpath)

```
src/main/resources
├── application.yml
├── application-local.yml
├── application-dev.yml
├── application-qa.yml
├── application-uat.yml
├── application-prod.yml
├── logback-spring.xml
└── messages/
    ├── messages.properties
    └── validation.properties
```

## Test layout

```
src/test/java/com/nmi/platform/skillexpert
├── AbstractIntegrationTest.java          # Testcontainers baseline
└── SkillExpertServiceApplicationTests.java
```

## Naming conventions

| Layer | Pattern |
|---|---|
| Controller | `*Controller` |
| Service interface | `*Service` |
| Service impl | `*ServiceImpl` |
| Repository | `*Repository` |
| Feign client | `*Client` |
| Request | `*Request` |
| Response | `*Response` |
| Mapper | `*Mapper` |

## Import rule

If a type already exists in `platform-common`, import it. Do not create parallel `BaseEntity`, `ApiResponse`, shared exception hierarchies, or duplicate Feign/cache/OpenAPI configuration.
