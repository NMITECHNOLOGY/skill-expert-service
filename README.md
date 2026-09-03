<!--
  Author: Viraj Sachin
  Created: 2026-09-03
  Copyright (c) 2026 NMI Infra Pvt Ltd
-->
# Skill Expert Service

Domain microservice for the **Skill Expert mini app**.

This repo is the **project foundation only** — same Spring Boot init style as `payment-service` / `control-panel-service`. No Skill Expert APIs or business logic yet.

## Platform backends (this workspace)

| Service | Port | Role |
|---|---|---|
| `auth-service` | 8443 | OAuth2.1 / OIDC authorization server (issues JWTs) |
| `loyalty-service` | 8080 | Merchants, wallets, points, rules |
| `payment-service` | 8088 | Card tokens + checkout (uses `platform-common`) |
| `control-panel-service` | 8085 | BFF for the Angular console (uses `platform-common`) |
| `platform-common` | — | Shared JAR (OpenAPI, Redis cache, Feign, correlation ID, `ApiResponse`) |
| **`skill-expert-service`** | **8089** | Skill Expert domain (this service) |

The Skill Expert **mini app** still runs on **8085** (module federation). This backend uses **8089** so they do not clash. Control Panel BFF also defaults to 8085 — do not run both on the same host port at once.

JWT issuer for this service is Auth: `http://localhost:8443`.

## Stack (matches payment / control-panel)

| Concern | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.x |
| Build | Apache Maven |
| Security | OAuth2 resource server + JWT + method security |
| Persistence | Spring Data JPA + PostgreSQL |
| Cache | Redis via `platform-common` |
| Integration | OpenFeign + Resilience4j (`@EnableFeignClients` here; interceptors from common) |
| API docs | SpringDoc via `platform-common` OpenAPI |
| Mapping | MapStruct + Lombok |
| Shared lib | `platform-common` |

## Package root

`com.nmi.platform.skillexpert` — today only `SkillExpertServiceApplication`, `config/`, and `security/`. Add `controller` / `service` / `repository` / `model` when features start. See [PackageStructure.md](PackageStructure.md).

## Prerequisites

- JDK 25+
- Maven 3.9+
- Docker (Postgres/Redis via Compose)

Install `platform-common` once:

```bash
mvn -DskipTests install
```

from `../platform-common`.

## Quick start (local)

```bash
./run-local.sh
```

Same pattern as `payment-service` / `loyalty-service` / `auth-service`. The script finds JDK 25, installs `platform-common` if missing, then runs with the `local` profile.

```bash
USE_DOCKER=1 ./run-local.sh     # start Postgres + Redis via Docker Compose
REQUIRE_REDIS=1 ./run-local.sh  # fail if Redis is down
REBUILD_COMMON=1 ./run-local.sh # rebuild platform-common first
```

Manual equivalent:

```bash
docker compose up -d postgres redis
mvn spring-boot:run -Plocal
```

Optional app container:

```bash
docker compose --profile app up -d --build
```

| | |
|---|---|
| Port | **8089** |
| Health | `GET /actuator/health` |
| OpenAPI | `http://localhost:8089/swagger-ui.html` |

Copy `.env.example` to `.env` to override DB / Redis / issuer. Default DB is `skill_expert_db_dev` (user `postgres` / `admin`).

## Maven profiles

| Profile | Spring profile | Purpose |
|---|---|---|
| `local` (default) | `local` | Developer laptop |
| `dev` | `dev` | Shared development |
| `qa` | `qa` | QA |
| `uat` | `uat` | UAT |
| `production` | `prod` | Production |
| `quality` | — | Checkstyle + SpotBugs + JaCoCo gate (70%) |

```bash
mvn test
mvn verify -Pquality
```

## What is **not** included yet

- Controllers, services, entities, DTOs
- Flyway / schema
- Feign client interfaces
- Business permissions and business tests

## Documentation

- [Architecture.md](Architecture.md)
- [PackageStructure.md](PackageStructure.md)
- [CodingStandards.md](CodingStandards.md)
- [APIStandards.md](APIStandards.md)

## Reuse `platform-common`

Do **not** copy OpenAPI, Redis cache, Feign interceptor, or correlation-filter config into this service. Those auto-configure from `platform-common`.

This service only owns: JWT resource-server filter chain, CORS, JPA scan packages, and `@EnableFeignClients`.
