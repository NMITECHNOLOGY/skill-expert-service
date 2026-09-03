<!--
  Author: Viraj Sachin
  Created: 2026-09-03
  Copyright (c) 2026 NMI Infra Pvt Ltd
-->
# API Standards

## Style

- REST over HTTPS
- JSON request/response bodies
- URL prefix: `/api/v1` (see `nmi.skill-expert.api-base-path`)
- Resource-oriented paths; prefer nouns over verbs

## Versioning

- URI versioning (`/api/v1/...`)
- Breaking changes require a new major version path

## Response envelope

Prefer `platform-common` wrappers:

- `ApiResponse<T>` for single payloads
- `PagedResponse<T>` for collections with paging metadata

Do not invent a parallel envelope.

## Errors

- RFC 7807 Problem Details (`application/problem+json`)
- Reuse `platform-common` exception mapping
- Include stable `type`, `title`, `status`, `detail`, and correlation identifiers when available

## Security

- `Authorization: Bearer <access_token>`
- OAuth2.1 / OIDC access tokens issued by Auth Service
- Document security schemes in OpenAPI (Bearer JWT)

## Pagination & filtering

- Prefer `page`, `size`, `sort` query params aligned with Spring Data
- Filtering query params should be explicit and documented per endpoint
- Default page size should be bounded (feature-level)

## Idempotency & safety

- GET/HEAD safe and idempotent
- PUT/DELETE idempotent where practical
- POST for creation / non-idempotent commands
- Support idempotency keys for critical writes when introduced

## OpenAPI

- SpringDoc serves `/v3/api-docs` and `/swagger-ui.html`
- Every public endpoint must have operation summary and response codes
- Tag controllers by Skill Expert capability area

## Integration guidelines

- Controllers stay thin; domain rules live in `service`
- Downstream calls go through Feign clients under `integration.*`
- Apply Resilience4j timeouts / circuit breakers on downstream calls
- Propagate correlation / request IDs (via `platform-common` Feign config)

## Compatibility with the Skill Expert mini app

- CORS origins configured per environment (local mini app on port 8085)
- Expose `Authorization`, `X-Correlation-Id`, `X-Request-Id`
- Prefer UTC timestamps in ISO-8601
