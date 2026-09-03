<!--
  Author: Viraj Sachin
  Created: 2026-09-03
  Copyright (c) 2026 NMI Infra Pvt Ltd
-->
# Coding Standards

## Principles

- SOLID, DRY, Clean Code
- Prefer composition over inheritance (except `platform-common` base types)
- Keep Skill Expert domain logic in this service; do not copy Auth / Loyalty / Payment rules
- Production-ready defaults; no speculative abstractions

## Language & style

- Java 25
- 4-space indentation, UTF-8, LF (see `.editorconfig`)
- No star imports
- Public types require class-level Javadoc
- Prefer records for immutable DTOs / properties
- Prefer constructor injection; avoid field injection

## Lombok & MapStruct

- Lombok config lives in `lombok.config`
- MapStruct component model: `spring`
- Unmapped MapStruct targets: `ERROR`
- Annotation processor order: Lombok → Lombok-MapStruct binding → MapStruct

## Spring conventions

- Configuration classes under `config` / `security` only for **this service**
- Do not re-create OpenAPI, Redis cache, Feign interceptor, or correlation-filter beans — they come from `platform-common`
- `@ConfigurationProperties` for typed externalized config
- Never enable `open-in-view`
- Transactions only around Skill Expert–owned persistence

## Security

- All endpoints authenticated unless explicitly permit-listed
- Use method security (`@PreAuthorize`) when permissions are introduced
- Do not log tokens, secrets, or PII
- Prefer `platform-common` JWT / security utilities

## Errors & APIs

- Reuse `platform-common` Problem Details / exception framework
- Return `ApiResponse` / `PagedResponse` wrappers from common
- Stable error types under `https://nmi.platform/problems/*`

## Logging & observability

- Structured logs with `correlationId` and `requestId` MDC keys
- Prefer INFO in prod; DEBUG only in local/dev
- Actuator liveness/readiness must remain cheap and dependency-aware

## Testing

- Unit tests: `*Test`
- Integration tests: `*IT` / extend `AbstractIntegrationTest`
- No business tests in the foundation phase
- Quality gate: `mvn verify -Pquality` (Checkstyle, SpotBugs, JaCoCo 70%)

## Git / delivery

- Feature slices: model → client → service → controller → test
- Do not introduce schema migration tooling until persistence is required
