#!/usr/bin/env bash
# Group D — PostgreSQL failures. Stack D: app 8083 -> toxiproxy :25433 -> postgres 5432 (voting_test), redis 6393.
source "$(dirname "$0")/lib.sh"
PORT=8083; RPORT=6393; PGPROXY=25433; OUT="$RESULTS_DIR/D"; mkdir -p "$OUT"
ensure_toxiproxy
tp delete pgD >/dev/null 2>&1; tp create -l localhost:$PGPROXY -u localhost:5432 pgD >/dev/null
start_redis $RPORT; start_app D $PORT $RPORT $PGPROXY; log "D healthy after $(wait_healthy $PORT)s"

run_with_fault() {
  local id=$1 conns=$2 total=$3 at=$4 fault=$5 rec_at=$6 recover=$7
  read -r P O <<<"$(create_poll $PORT 2)"
  node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode vote --connections "$conns" --duration "$total" \
    --userStart $((RANDOM * 10000)) --label "$id" --out "$OUT/$id-load.json" >/dev/null &
  local lpid=$!
  probe $PORT "$P" "${O%%,*}" "$OUT/$id-probe.csv" "$total" &
  local ppid=$!
  sleep "$at"; log "$id: FAULT -> $fault"; eval "$fault"
  ( sleep 3; echo "queue during fault (+3s): $(queue_len $RPORT)" >> "$OUT/$id.txt" ) &
  sleep $((rec_at - at)); log "$id: RECOVER -> $recover"; eval "$recover"
  echo "queue at recovery: $(queue_len $RPORT)" >> "$OUT/$id.txt"
  wait $lpid $ppid
  echo "$id load: $(python3 -c "import json;d=json.load(open('$OUT/$id-load.json'));print({k:d[k] for k in ('requests_total','rps_avg','status_codes','errors','timeouts')}, d['latency_ms'])")" >> "$OUT/$id.txt"
  echo "$id probe: $(summarize_probe "$OUT/$id-probe.csv")" >> "$OUT/$id.txt"
  local S; S=$(date +%s)
  if wait_queue_drained $RPORT 180; then echo "$id queue drained $(( $(date +%s) - S ))s after load end" >> "$OUT/$id.txt"
  else echo "$id queue NOT drained after 180s: $(queue_len $RPORT)" >> "$OUT/$id.txt"; fi
  local acc; acc=$(python3 -c "import json,csv;d=json.load(open('$OUT/$id-load.json'));p=sum(1 for r in csv.DictReader(open('$OUT/$id-probe.csv')) if r['vote_code']=='200');print(d['accepted_2xx']+p)")
  python3 "$RT_DIR/scripts/consistency_check.py" $RPORT "$P" "$O" "$acc" > "$OUT/$id-consistency.json"
  python3 -c "import json;d=json.load(open('$OUT/$id-consistency.json'));print('$id consistency: accepted',d['client_accepted'],'pg_rows',d['pg_rows'],'redis_total',d['redis_total'],'queued',d['queued'],'lost_in_pg',d['lost_in_pg'],'consistent',d['consistent'])" >> "$OUT/$id.txt"
  cat "$OUT/$id.txt"
}

# D1 PostgreSQL unreachable for 30 s (connection refused), load 20 conns, 70 s
run_with_fault D1-pg-down 20 70 15 "tp toggle pgD >/dev/null" 45 "tp toggle pgD >/dev/null"

# D2 PostgreSQL slow: +2 s latency for 25 s, load 50 conns, 60 s
run_with_fault D2-pg-slow 50 60 15 "tp toxic add -t latency -a latency=2000 -n slow pgD >/dev/null" 40 "tp toxic remove -n slow pgD >/dev/null"
log "D2: health UP again $(wait_healthy $PORT 120)s after load ended" | tee -a "$OUT/D2-pg-slow.txt"

# D4 poison entry: one duplicate (already stored) vote in the queue together with 50 new ones
read -r P O <<<"$(create_poll $PORT 2)"; OPT1=${O%%,*}
curl -s -o /dev/null -X POST localhost:$PORT/poll/$P/vote -H 'Content-Type: application/json' -d "{\"userId\":1,\"optionId\":$OPT1}"
wait_queue_drained $RPORT 30
redis-cli -p $RPORT rpush vote:queue "$P:1:$OPT1" >/dev/null   # same (user, poll) again -> unique constraint
for u in $(seq 2 51); do redis-cli -p $RPORT rpush vote:queue "$P:$u:$OPT1" >/dev/null; done
sleep 30
{
  echo "D4 after 30 s: queue length=$(queue_len $RPORT) (51 injected; 0 means handled)"
  echo "D4 good votes stored: $(psql_test "select count(*) from vote where poll_id=$P and user_id between 2 and 51") of 50"
  echo "D4 'Failed to persist' errors in log: $(grep -c 'Failed to persist' "$RUN_DIR/app-D.log")"
} | tee "$OUT/D4.txt"

stop_app D; stop_redis $RPORT; tp delete pgD >/dev/null; log "group D done"
