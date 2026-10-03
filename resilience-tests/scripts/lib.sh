#!/usr/bin/env bash
# Shared helpers for resilience tests. Source this file: `source scripts/lib.sh`
# Each "stack" = one app instance + its own Redis, against the local voting_test DB,
# so tests can run side by side without touching the real app (8080) or its Redis (6379).

set -uo pipefail

RT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$RT_DIR/.." && pwd)"
JAR="$REPO_DIR/target/voting-0.0.1-SNAPSHOT.jar"
RUN_DIR="$RT_DIR/.run"          # pids, logs, redis data (gitignored)
RESULTS_DIR="$RT_DIR/results"
PG_BIN="/opt/homebrew/opt/postgresql@14/bin"
TEST_DB="${TEST_DB:-voting_test}"
mkdir -p "$RUN_DIR" "$RESULTS_DIR"

log() { echo "[$(date +%H:%M:%S)] $*"; }

# start_redis <port>  — plain redis-server with default persistence (same as brew: RDB snapshots, no AOF)
start_redis() {
  local port=$1 dir="$RUN_DIR/redis-$1"
  mkdir -p "$dir"
  redis-server --port "$port" --dir "$dir" --daemonize yes --logfile "$dir/redis.log" --pidfile "$dir/redis.pid" >/dev/null
  for _ in $(seq 1 20); do redis-cli -p "$port" ping >/dev/null 2>&1 && return 0; sleep 0.2; done
  echo "redis $port failed to start" >&2; return 1
}
stop_redis() { redis-cli -p "$1" shutdown nosave >/dev/null 2>&1 || true; }

# start_app <name> <app_port> <redis_port> [db_port] [extra env...]
# db_port defaults to 5432; pass a toxiproxy port to put a proxy in front of PostgreSQL.
start_app() {
  local name=$1 port=$2 rport=$3 dbport=${4:-5432}
  shift 4 2>/dev/null || shift $#
  env SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${dbport}/${TEST_DB}" \
      SPRING_DATASOURCE_USERNAME="$(whoami)" SPRING_DATASOURCE_PASSWORD="" \
      SPRING_REDIS_HOST=localhost SPRING_REDIS_PORT="$rport" SPRING_REDIS_PASSWORD="" \
      SERVER_PORT="$port" HEALTH_SHOW_DETAILS=always SPRING_JPA_SHOW_SQL=false \
      "$@" \
      nohup java -jar "$JAR" > "$RUN_DIR/app-$name.log" 2>&1 &
  echo $! > "$RUN_DIR/app-$name.pid"
}

# stop_app <name> [signal]  — default TERM (graceful); pass KILL for kill -9
stop_app() {
  local name=$1 sig=${2:-TERM} pidf="$RUN_DIR/app-$1.pid"
  [[ -f $pidf ]] || return 0
  kill -"$sig" "$(cat "$pidf")" 2>/dev/null || true
  for _ in $(seq 1 30); do kill -0 "$(cat "$pidf")" 2>/dev/null || break; sleep 0.5; done
  rm -f "$pidf"
}

# wait_healthy <port> [timeout_s] — prints seconds taken, returns 1 on timeout
wait_healthy() {
  local port=$1 timeout=${2:-90} start=$(date +%s)
  while (( $(date +%s) - start < timeout )); do
    if curl -s -m 2 "localhost:$port/actuator/health" | grep -q '"status":"UP"'; then
      echo $(( $(date +%s) - start )); return 0
    fi
    sleep 0.5
  done
  echo "timeout"; return 1
}

# create_poll <port> [n_options] — prints: pollId opt1,opt2,...
create_poll() {
  local port=$1 n=${2:-2} opts
  opts=$(python3 -c "import json;print(json.dumps(['Opt%d'%i for i in range($n)]))")
  curl -s -X POST "localhost:$port/poll" -H 'Content-Type: application/json' \
    -d "{\"question\":\"resilience test\",\"options\":$opts,\"expiresAt\":\"2099-12-31T23:59:59Z\"}" |
    python3 -c 'import sys,json;b=json.load(sys.stdin);print(b["pollId"], ",".join(str(o["optionId"]) for o in b["options"]))'
}

psql_test() { "$PG_BIN/psql" -d "$TEST_DB" -Atc "$1"; }

# queue_len <redis_port>
queue_len() { redis-cli -p "$1" llen vote:queue; }

# wait_queue_drained <redis_port> [timeout_s]
wait_queue_drained() {
  local rport=$1 timeout=${2:-120} start=$(date +%s)
  while (( $(date +%s) - start < timeout )); do
    [[ "$(queue_len "$rport")" == "0" ]] && { sleep 6; [[ "$(queue_len "$rport")" == "0" ]] && return 0; }
    sleep 1
  done
  return 1
}

# probe <port> <poll_id> <option_id> <out_csv> <seconds> [user_start]
# Every 0.5 s: one health check and one vote (unique user), logging code + latency.
# Columns: t_rel_s,health_code,health_s,vote_code,vote_s   (code 000 = no response within 5 s)
probe() {
  local port=$1 poll=$2 opt=$3 out=$4 secs=$5 user=${6:-$((RANDOM * 1000 + 900000000))}
  local start end now h v
  start=$(python3 -c 'import time;print(time.time())')
  end=$(( $(date +%s) + secs ))
  echo "t_rel_s,health_code,health_s,vote_code,vote_s" > "$out"
  while (( $(date +%s) < end )); do
    now=$(python3 -c "import time;print(round(time.time()-$start,1))")
    h=$(curl -s -o /dev/null -m 5 -w '%{http_code},%{time_total}' "localhost:$port/actuator/health")
    v=$(curl -s -o /dev/null -m 5 -w '%{http_code},%{time_total}' -X POST "localhost:$port/poll/$poll/vote" \
        -H 'Content-Type: application/json' -d "{\"userId\":$user,\"optionId\":$opt}")
    echo "$now,$h,$v" >> "$out"
    user=$((user + 1))
    sleep 0.5
  done
}

# summarize_probe <csv> — prints per-phase counts of status codes
summarize_probe() {
  python3 - "$1" <<'PY'
import csv,sys,collections
rows=list(csv.DictReader(open(sys.argv[1])))
hc=collections.Counter(r["health_code"] for r in rows); vc=collections.Counter(r["vote_code"] for r in rows)
slow=[r for r in rows if float(r["vote_s"])>=4.9 or float(r["health_s"])>=4.9]
print(f"probes={len(rows)} health={dict(hc)} vote={dict(vc)} hung(>=5s)={len(slow)}")
PY
}

# toxiproxy helpers (server on :8474)
tp() { toxiproxy-cli "$@"; }
ensure_toxiproxy() {
  curl -s localhost:8474/version >/dev/null 2>&1 && return 0
  nohup toxiproxy-server > "$RUN_DIR/toxiproxy.log" 2>&1 &
  for _ in $(seq 1 20); do curl -s localhost:8474/version >/dev/null 2>&1 && return 0; sleep 0.2; done
  return 1
}
