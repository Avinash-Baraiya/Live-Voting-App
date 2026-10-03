#!/usr/bin/env bash
# Group C — Redis failures. Stack C: app 8082 -> toxiproxy :26392 -> redis 6392, local voting_test DB.
source "$(dirname "$0")/lib.sh"
PORT=8082; RPORT=6392; PROXY=26392; OUT="$RESULTS_DIR/C"; mkdir -p "$OUT"
ensure_toxiproxy
tp delete redisC >/dev/null 2>&1; tp create -l localhost:$PROXY -u localhost:$RPORT redisC >/dev/null
start_redis $RPORT; start_app C $PORT $PROXY 5432; log "C healthy after $(wait_healthy $PORT)s"

# run_with_fault <id> <conns> <total_s> <fault_at_s> <fault_cmd> <recover_at_s> <recover_cmd>
run_with_fault() {
  local id=$1 conns=$2 total=$3 at=$4 fault=$5 rec_at=$6 recover=$7
  read -r P O <<<"$(create_poll $PORT 2)"
  node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode vote --connections "$conns" --duration "$total" \
    --userStart $((RANDOM * 10000)) --label "$id" --out "$OUT/$id-load.json" >/dev/null &
  local lpid=$!
  probe $PORT "$P" "${O%%,*}" "$OUT/$id-probe.csv" "$total" &
  local ppid=$!
  sleep "$at"; log "$id: FAULT -> $fault"; eval "$fault"
  sleep $((rec_at - at)); log "$id: RECOVER -> $recover"; eval "$recover"
  local t0; t0=$(date +%s)
  wait $lpid $ppid
  echo "$id health after recover: $(curl -s -m 5 localhost:$PORT/actuator/health | head -c 200)" >> "$OUT/$id.txt"
  echo "$id load: $(python3 -c "import json;d=json.load(open('$OUT/$id-load.json'));print({k:d[k] for k in ('requests_total','rps_avg','status_codes','errors','timeouts')}, d['latency_ms'])")" >> "$OUT/$id.txt"
  echo "$id probe: $(summarize_probe "$OUT/$id-probe.csv")" >> "$OUT/$id.txt"
  echo "$P $O" > "$OUT/$id.poll"
}

# C4 Redis slow: +200 ms latency on every Redis call for 20 s (load 50 conns, 50 s)
run_with_fault C4-redis-slow 50 50 15 "tp toxic add -t latency -a latency=200 -n slow redisC >/dev/null" 35 "tp toxic remove -n slow redisC >/dev/null"
cat "$OUT/C4-redis-slow.txt"

# C2 Redis hangs: CLIENT PAUSE 30 s (every command blocks), load 100 conns, 70 s
run_with_fault C2-redis-hang 100 70 15 "redis-cli -p $RPORT client pause 30000 all >/dev/null" 45 "redis-cli -p $RPORT client unpause >/dev/null"
cat "$OUT/C2-redis-hang.txt"
log "C2: time for health to be UP again: $(wait_healthy $PORT 120)s after load ended" | tee -a "$OUT/C2-redis-hang.txt"

# C1 Redis crash (kill -9) for 20 s then restart from its snapshot, load 20 conns, 60 s  (also C3 data loss, C5 recovery)
read -r PB OB <<<"$(create_poll $PORT 2)"   # a poll that exists before the crash, to measure data loss
node "$RT_DIR/scripts/load.js" --port $PORT --poll "$PB" --options "$OB" --mode vote --connections 20 --amount 3000 \
  --userStart 1 --label C1-before --out "$OUT/C1-before-load.json" >/dev/null
wait_queue_drained $RPORT 60
BEFORE=$(python3 "$RT_DIR/scripts/consistency_check.py" $RPORT "$PB" "$OB" 3000)
echo "$BEFORE" > "$OUT/C1-before-consistency.json"
run_with_fault C1-redis-crash 20 60 15 "kill -9 \$(cat $RUN_DIR/redis-$RPORT/redis.pid)" 35 "start_redis $RPORT"
cat "$OUT/C1-redis-crash.txt"
wait_queue_drained $RPORT 120
python3 "$RT_DIR/scripts/consistency_check.py" $RPORT "$PB" "$OB" 3000 > "$OUT/C3-after-crash-consistency.json"
{
  echo "C3 poll voted BEFORE the crash (3000 accepted, all in PostgreSQL):"
  python3 -c "import json;d=json.load(open('$OUT/C3-after-crash-consistency.json'));print('   redis_total(results shown)=',d['redis_total'],' redis_voters=',d['redis_voters'],' pg_rows=',d['pg_rows'])"
  echo "C3 results endpoint now: $(curl -s localhost:$PORT/poll/$PB/results)"
} | tee "$OUT/C3.txt"
read -r P O < "$OUT/C1-redis-crash.poll"
ACC=$(python3 -c "import json,csv;d=json.load(open('$OUT/C1-redis-crash-load.json'));p=sum(1 for r in csv.DictReader(open('$OUT/C1-redis-crash-probe.csv')) if r['vote_code']=='200');print(d['accepted_2xx']+p)")
python3 "$RT_DIR/scripts/consistency_check.py" $RPORT "$P" "$O" "$ACC" > "$OUT/C1-during-crash-consistency.json"
python3 -c "import json;d=json.load(open('$OUT/C1-during-crash-consistency.json'));print('C1 poll voted DURING crash: accepted',d['client_accepted'],'pg_rows',d['pg_rows'],'redis_total',d['redis_total'],'lost_in_pg',d['lost_in_pg'],'consistent',d['consistent'])" | tee -a "$OUT/C3.txt"

stop_app C; stop_redis $RPORT; tp delete redisC >/dev/null; log "group C done"
