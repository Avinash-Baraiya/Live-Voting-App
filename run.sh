#!/usr/bin/env bash
# One command to run the app locally (Redis + PostgreSQL in Docker).
#
#   ./run.sh local          start Redis + PostgreSQL in Docker, check them, run the app with Maven
#   ./run.sh local --fast   same, but start the built jar (~2 s); rebuilds only when code changed
#   ./run.sh smoke          check the app that is already running
#   ./run.sh stop           stop all Docker containers, Kafka and monitoring included (data is kept)
#
# Settings come from .env.local (gitignored; create it with: cp .env.local.example .env.local).
set -euo pipefail
cd "$(dirname "$0")"

say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '    \033[32mOK\033[0m   %s\n' "$*"; }
fail() { printf '    \033[31mFAIL\033[0m %s\n' "$*"; exit 1; }

# Can we open a TCP connection to host:port within 5 seconds?
reachable() { nc -z -G 5 "$1" "$2" >/dev/null 2>&1; }

preflight() {
  say "Checking Redis and PostgreSQL"

  # Redis: TCP first, then a PING if redis-cli is installed.
  reachable "$SPRING_REDIS_HOST" "$SPRING_REDIS_PORT" \
    || fail "Redis $SPRING_REDIS_HOST:$SPRING_REDIS_PORT is not reachable (is the container running?)"
  if command -v redis-cli >/dev/null 2>&1; then
    REDISCLI_AUTH="${SPRING_REDIS_PASSWORD:-}" redis-cli -n "${SPRING_REDIS_DATABASE:-0}" -h "$SPRING_REDIS_HOST" -p "$SPRING_REDIS_PORT" ping 2>/dev/null \
      | grep -q PONG || fail "Redis $SPRING_REDIS_HOST answered but PING failed (wrong password?)"
  fi
  ok "Redis $SPRING_REDIS_HOST:$SPRING_REDIS_PORT (database ${SPRING_REDIS_DATABASE:-0})"

  # PostgreSQL: pull host and port out of jdbc:postgresql://host:port/db
  local hostport host port
  hostport=$(sed -E 's#^jdbc:postgresql://([^/?]+).*#\1#' <<<"$SPRING_DATASOURCE_URL")
  host=${hostport%%:*}; port=${hostport##*:}; [[ "$port" == "$host" ]] && port=5432
  reachable "$host" "$port" || fail "PostgreSQL $host:$port is not reachable (is the container running?)"
  ok "PostgreSQL $host:$port"
}

smoke() {
  local base="http://localhost:${SERVER_PORT:-8080}"
  say "Smoke test against $base"
  curl -sf "$base/actuator/health" >/dev/null || fail "app is not answering on $base (is it running?)"
  curl -s "$base/actuator/health" | python3 -c '
import json, sys
h = json.load(sys.stdin)
print("    status:", h["status"])
for name in ("db", "redis", "voteQueue"):
    c = h.get("components", {}).get(name)
    if c: print(f"    {name:9}", c["status"])
'
  curl -s "$base/actuator/info" | python3 -c '
import json, sys
i = json.load(sys.stdin)
for name, b in i.get("backends", {}).items():
    print(f"    {name:9}", b["endpoint"])
'
}

case "${1:-}" in
  local)
    [[ -f .env.local ]] || fail ".env.local not found. Create it: cp .env.local.example .env.local"

    say "Starting Redis and PostgreSQL containers"
    docker compose --profile redis --profile postgres up -d --wait

    # 'set -a' exports every variable the env file defines, so the app inherits them.
    set -a; source .env.local; set +a
    preflight

    mvn_cmd=mvn; command -v mvn >/dev/null 2>&1 || mvn_cmd=./mvnw
    if [[ "${2:-}" == "--fast" ]]; then
      # Rebuild the jar only when code or pom.xml changed since the last build.
      jar=$(ls target/voting-*.jar 2>/dev/null | grep -v plain | head -1 || true)
      if [[ -z "$jar" ]] || [[ -n "$(find src pom.xml -newer "$jar" -type f 2>/dev/null | head -1)" ]]; then
        say "Code changed since the last build: packaging the jar (tests skipped)"
        $mvn_cmd -q package -DskipTests
        jar=$(ls target/voting-*.jar | grep -v plain | head -1)
      fi
      say "Starting $jar (Ctrl+C to stop). In another terminal: ./run.sh smoke"
      exec java -jar "$jar"
    fi
    say "Starting the app with Maven (Ctrl+C to stop). In another terminal: ./run.sh smoke"
    exec $mvn_cmd -q spring-boot:run
    ;;
  smoke)
    smoke ;;
  stop)
    say "Stopping Docker containers (volumes kept; use 'docker compose down -v' to delete data)"
    docker compose --profile redis --profile postgres --profile kafka --profile monitoring down ;;
  *)
    sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
