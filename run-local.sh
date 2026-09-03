#!/usr/bin/env bash
# Author: Viraj Sachin
# Created: 2026-09-03
# Copyright (c) 2026 NMI Infra Pvt Ltd
#
# Run skill-expert-service locally with JDK 25 + local Spring profile.
# Uses the PostgreSQL and Redis already installed on this machine; Docker is
# only involved if you ask for it. Installs platform-common when missing.
#
#   ./run-local.sh                  run against local PostgreSQL/Redis
#   USE_DOCKER=1 ./run-local.sh     start PostgreSQL/Redis via Docker Compose
#   REQUIRE_REDIS=1 ./run-local.sh  fail instead of running without Redis
#   REBUILD_COMMON=1 ./run-local.sh force a rebuild of platform-common first
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

resolve_java25() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    local ver
    ver="$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1 || true)"
    if [[ "$ver" == *"version \"25"* ]] || [[ "$ver" == *"version \"26"* ]]; then
      echo "$JAVA_HOME"
      return 0
    fi
  fi

  local candidates=(
    "${HOME}/.jdks/openjdk-25.0.2"
    "${HOME}/.jdks/openjdk-25.0.1"
    "${HOME}/.jdks/openjdk-25"
    "/usr/lib/jvm/java-25-openjdk-amd64"
    "/usr/lib/jvm/java-25-openjdk"
  )

  if [[ -d "${HOME}/.jdks" ]]; then
    local d
    for d in "${HOME}"/.jdks/openjdk-25* "${HOME}"/.jdks/openjdk-26*; do
      if [[ -x "${d}/bin/java" ]]; then
        candidates+=("$d")
      fi
    done
  fi

  local c
  for c in "${candidates[@]}"; do
    if [[ -x "${c}/bin/java" ]]; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

load_dotenv() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  local line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    key="$(echo "$key" | xargs)"
    [[ -z "$key" ]] && continue
    if [[ -z "${!key:-}" ]]; then
      export "$key=$value"
    fi
  done < "$file"
}

if ! JAVA25_HOME="$(resolve_java25)"; then
  echo "ERROR: JDK 25 is required to run skill-expert-service."
  echo "Install OpenJDK 25, or set JAVA_HOME to a JDK 25 install."
  exit 1
fi

export JAVA_HOME="$JAVA25_HOME"
export PATH="${JAVA_HOME}/bin:${PATH}"

if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: Maven (mvn) is required. Install Maven 3.9+ and retry."
  exit 1
fi

MVN_VERSION="$(mvn -v 2>/dev/null | awk '/^Apache Maven/ { print $3; exit }')"
if [[ -n "$MVN_VERSION" ]]; then
  MVN_MAJOR="${MVN_VERSION%%.*}"
  MVN_MINOR="$(echo "$MVN_VERSION" | cut -d. -f2)"
  if (( MVN_MAJOR < 3 || (MVN_MAJOR == 3 && MVN_MINOR < 9) )); then
    echo "WARNING: Maven ${MVN_VERSION} detected; this project targets Maven 3.9+."
  fi
fi

# Optional local overrides (see .env.example). Existing shell env wins.
load_dotenv "${ROOT}/.env"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
export SERVER_PORT="${SERVER_PORT:-8089}"
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-skill_expert_db_dev}"
export DB_USER="${DB_USER:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-Viraj@12345}"
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-}"
export OAUTH2_ISSUER_URI="${OAUTH2_ISSUER_URI:-http://localhost:8443}"

ensure_platform_common() {
  local common_dir="${ROOT}/../platform-common"
  local version
  version="$(
    awk -F'[<>]' '/<platform-common.version>/ { print $3; exit }' "${ROOT}/pom.xml"
  )"
  version="${version:-1.0.0-SNAPSHOT}"

  local artifact_dir="${HOME}/.m2/repository/com/nmi/platform/platform-common/${version}"
  local installed=0
  if compgen -G "${artifact_dir}/platform-common-*.jar" >/dev/null 2>&1; then
    installed=1
  fi

  if [[ "$installed" == "1" && "${REBUILD_COMMON:-0}" != "1" ]]; then
    echo "platform-common ${version} found in local Maven repo."
    return 0
  fi

  if [[ ! -f "${common_dir}/pom.xml" ]]; then
    echo "ERROR: platform-common ${version} is not installed and its source was"
    echo "not found at ${common_dir}."
    echo "Clone platform-common next to skill-expert-service, or run:"
    echo "  mvn -DskipTests install   # inside your platform-common checkout"
    exit 1
  fi

  echo "Building platform-common ${version} from ${common_dir}..."
  if [[ -x "${common_dir}/build-local.sh" ]]; then
    SKIP_TESTS=1 "${common_dir}/build-local.sh"
  else
    (cd "$common_dir" && mvn -B -DskipTests clean install)
  fi
}

port_in_use() {
  (echo >/dev/tcp/"$1"/"$2") >/dev/null 2>&1
}

skill_expert_db_ready() {
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
      -d "$DB_NAME" -Atc 'select 1' >/dev/null 2>&1
    return $?
  fi
  if command -v pg_isready >/dev/null 2>&1; then
    pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1
    return $?
  fi
  port_in_use "$DB_HOST" "$DB_PORT"
}

redis_probe() {
  exec 3<>/dev/tcp/"$REDIS_HOST"/"$REDIS_PORT" || return 1
  printf 'PING\r\n' >&3
  local reply=""
  read -r -t 2 reply <&3 || true
  exec 3<&- 3>&-
  printf '%s' "$reply"
}

redis_ready() {
  if command -v redis-cli >/dev/null 2>&1; then
    local pong
    pong="$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping 2>/dev/null || true)"
    [[ "$pong" == "PONG" || "$pong" == *"NOAUTH"* ]]
    return $?
  fi
  local reply
  reply="$(redis_probe 2>/dev/null)" || return 1
  [[ "$reply" == "+PONG"* || "$reply" == "-NOAUTH"* ]]
}

report_missing_database() {
  echo "ERROR: cannot reach database '${DB_NAME}' at ${DB_HOST}:${DB_PORT} as '${DB_USER}'."
  echo
  if port_in_use "$DB_HOST" "$DB_PORT"; then
    echo "A PostgreSQL server is listening, but that role/database is missing or"
    echo "the password differs. Create them once:"
    echo
    echo "  sudo -u postgres psql -c \"CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';\""
    echo "  sudo -u postgres psql -c \"CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};\""
    echo
    echo "To use different credentials instead, put DB_NAME / DB_USER / DB_PASSWORD"
    echo "in skill-expert-service/.env (see .env.example)."
  else
    echo "Nothing is listening on ${DB_HOST}:${DB_PORT}. Start PostgreSQL:"
    echo
    echo "  sudo systemctl start postgresql"
    echo
    echo "Or run the bundled container instead: USE_DOCKER=1 ./run-local.sh"
  fi
  exit 1
}

start_with_docker() {
  local services=("$@")
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: USE_DOCKER=1 but Docker is not installed."
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "ERROR: USE_DOCKER=1 but the Docker daemon is not running."
    echo "Start it with: sudo systemctl start docker"
    exit 1
  fi

  local svc port
  for svc in "${services[@]}"; do
    if [[ "$svc" == "postgres" ]]; then port="$DB_PORT"; else port="$REDIS_PORT"; fi
    if port_in_use localhost "$port"; then
      echo "ERROR: port ${port} is already taken, so Compose cannot bind ${svc}."
      echo "Free the port, or choose another with DB_PORT= / REDIS_PORT=."
      exit 1
    fi
  done

  echo "Starting ${services[*]} (docker compose up -d)..."
  docker compose up -d "${services[@]}"
}

postgres_login_ready() {
  if ! command -v psql >/dev/null 2>&1; then
    return 1
  fi
  PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
    -d postgres -Atc 'select 1' >/dev/null 2>&1
}

create_database_if_missing() {
  if skill_expert_db_ready; then
    return 0
  fi
  if ! postgres_login_ready; then
    return 1
  fi
  echo "Creating database '${DB_NAME}' owned by '${DB_USER}'..."
  PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
    -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"
}

ensure_postgres() {
  if skill_expert_db_ready; then
    echo "PostgreSQL: database '${DB_NAME}' reachable at ${DB_HOST}:${DB_PORT}."
    return 0
  fi

  if create_database_if_missing && skill_expert_db_ready; then
    echo "PostgreSQL: database '${DB_NAME}' created at ${DB_HOST}:${DB_PORT}."
    return 0
  fi

  if [[ "${USE_DOCKER:-0}" != "1" ]]; then
    report_missing_database
  fi

  start_with_docker postgres
  local attempt
  for attempt in {1..30}; do
    if skill_expert_db_ready; then
      echo "PostgreSQL is ready."
      return 0
    fi
    sleep 1
  done
  echo "ERROR: PostgreSQL did not become ready at ${DB_HOST}:${DB_PORT}."
  exit 1
}

ensure_redis() {
  if redis_ready; then
    echo "Redis: reachable at ${REDIS_HOST}:${REDIS_PORT}."
    return 0
  fi

  if [[ "${USE_DOCKER:-0}" == "1" ]]; then
    start_with_docker redis
    local attempt
    for attempt in {1..30}; do
      if redis_ready; then
        echo "Redis is ready."
        return 0
      fi
      sleep 1
    done
    echo "ERROR: Redis did not become ready at ${REDIS_HOST}:${REDIS_PORT}."
    exit 1
  fi

  if [[ "${REQUIRE_REDIS:-0}" == "1" ]]; then
    echo "ERROR: REQUIRE_REDIS=1 but Redis is not reachable at ${REDIS_HOST}:${REDIS_PORT}."
    exit 1
  fi

  CACHE_DISABLED=1
  echo "Redis: not reachable at ${REDIS_HOST}:${REDIS_PORT} — running without it."
  echo "       Caching becomes a no-op and the Redis health check is switched off."
  echo "       Install it (sudo apt install redis-server), use USE_DOCKER=1, or set"
  echo "       REQUIRE_REDIS=1 to make this an error instead."
}

CACHE_DISABLED=0

ensure_platform_common
ensure_postgres
ensure_redis

if command -v curl >/dev/null 2>&1; then
  if ! curl -sfo /dev/null --max-time 3 \
    "${OAUTH2_ISSUER_URI%/}/.well-known/openid-configuration"; then
    echo "WARNING: auth-service is not answering at ${OAUTH2_ISSUER_URI}."
    echo "         skill-expert-service will fail to start until it is running."
  fi
fi

MVN_RUN_ARGS=(spring-boot:run -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE}")

if [[ "$CACHE_DISABLED" == "1" ]]; then
  MVN_RUN_ARGS+=(
    "-Dspring-boot.run.jvmArguments=-Dspring.cache.type=none -Dmanagement.health.redis.enabled=false -Dmanagement.endpoint.health.validate-group-membership=false"
  )
fi

echo "Using JAVA_HOME=${JAVA_HOME}"
echo "Using SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}"
echo "Using database ${DB_NAME} at ${DB_HOST}:${DB_PORT} as ${DB_USER}"
echo "Skill Expert service listens on http://localhost:${SERVER_PORT}"
echo "Swagger UI: http://localhost:${SERVER_PORT}/swagger-ui.html"
java -version

exec mvn "${MVN_RUN_ARGS[@]}" "$@"
