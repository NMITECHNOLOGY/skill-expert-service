<!--
  Author: Viraj Sachin
  Created: 2026-09-03
  Copyright (c) 2026 NMI Infra Pvt Ltd
-->
# Architecture — Skill Expert Service

## Role

Skill Expert Service is the **domain microservice** for the Skill Expert mini app. It will own Skill Expert business data and APIs (experts, skills, bookings, and related workflows) when those features are added.

It **must not** re-implement authentication, loyalty wallets, or payment capture. Those stay in Auth, Loyalty, and Payment services.

## Platform context

```
Skill Expert mini app (port 8085)
          |
          | HTTPS / REST (OAuth2 bearer)
          v
   Skill Expert Service (port 8089)
          |
          | OpenFeign + Resilience4j
          +-- Auth Service
          +-- Loyalty Service
          +-- Payment Service
          +-- Notification Service (future)
          |
          +-- PostgreSQL (service-owned data only)
          +-- Redis (cache / short-lived state)
```

### Existing services

| Service | Responsibilities |
|---|---|
| Auth Service | Authentication, users, roles, permissions, OAuth clients, tokens |
| Loyalty Service | Merchants, stores, wallets, points, rules, ledger, campaigns |
| Payment Service | Card token orchestration, checkout, loyalty+card funding |
| Control Panel Service | BFF for the Angular Platform Console |

### Skill Expert responsibilities (target)

- Expert profiles and skill catalog
- Discovery / listing for the mini app
- Booking and session lifecycle (when introduced)
- Service-owned persistence for the Skill Expert domain
- Downstream calls to Auth, Loyalty, and Payment as needed

## Architectural style

This is a **domain service**, not a BFF. Controllers expose Skill Expert APIs. Downstream clients are used only to reuse other domains, not to duplicate them.

## Design principles

1. **Own the Skill Expert domain** — persist and serve Skill Expert data here.
2. **Reuse `platform-common`** — shared contracts, exceptions, security utilities, Feign/cache/OpenAPI baselines.
3. **Security by default** — OAuth2 Resource Server, JWT authorities mapping, method security enabled.
4. **Observability first** — Actuator probes, Micrometer/Prometheus, structured logs with correlation/request IDs.
5. **Incremental delivery** — no domain classes yet; features land as vertical slices.

## Technology baseline

- Java 25, Spring Boot 4.1.x, Spring Cloud 2025.1.x (OpenFeign / Circuit Breaker)
- Spring Security OAuth2 Resource Server
- JPA + PostgreSQL, Redis cache
- Virtual threads for request and async execution
- MapStruct / Lombok
- Testcontainers foundation for integration tests

## Security

- Stateless JWT resource server (issuer: Auth Service, local `http://localhost:8443`)
- CORS configured for the Skill Expert mini app origin
- Public: health/info/prometheus + OpenAPI UI (tighten in prod as required)
- Method security enabled; permission implementations deferred

## Integration foundation

`platform-common` supplies OpenAPI, Redis cache, Feign interceptors / error decoding, and the correlation-ID filter.

This service only adds `@EnableFeignClients` (`OpenFeignConfig`). Add Feign interfaces under `integration.<downstream>` when a feature needs them.

## Evolution

Features should be added as thin vertical slices:

1. Request/response models (reusing common wrappers)
2. Entities / repositories when persistence is required
3. Feign clients for required downstream calls
4. Domain services
5. Controllers
6. Tests extending `AbstractIntegrationTest`

Do not introduce Flyway/Liquibase or domain tables until a Skill Expert–owned persistence need is proven.
