#!/usr/bin/env bash
# Group E — app crash and restart. Stack E: app 8084, redis 6394, local voting_test DB.
source "$(dirname "$0")/lib.sh"
PORT=8084; RPORT=6394; OUT="$RESULTS_DIR/E"; mkdir -p "$OUT"
start_redis $RPORT
S=$(date +%s); start_app E $PORT $RPORT 5432
echo "E3 cold start to healthy: $(wait_healthy $PORT)s" | tee "$OUT/E3.txt"

# E1 kill -9 under load (50 conns). Compare what clients were told (200) with what was stored.
read -r P O <<<"$(create_poll $PORT 2)"
node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode vote --connections 50 --duration 25 \
  --userStart 1 --label E1 --out "$OUT/E1-load.json" >/dev/null &
LP=$!
sleep 10; log "E1: kill -9 app"; Q=$(queue_len $RPORT); stop_app E KILL
echo "E1 queue at kill: $Q" > "$OUT/E1.txt"
wait $LP
ACC=$(python3 -c "import json;print(json.load(open('$OUT/E1-load.json'))['accepted_2xx'])")
S=$(date +%s); start_app E $PORT $RPORT 5432
echo "E3 restart after crash to healthy: $(wait_healthy $PORT)s" | tee -a "$OUT/E3.txt"
wait_queue_drained $RPORT 60
python3 "$RT_DIR/scripts/consistency_check.py" $RPORT "$P" "$O" "$ACC" > "$OUT/E1-consistency.json"
python3 -c "
import json;d=json.load(open('$OUT/E1-consistency.json'))
print('E1 accepted(200)',d['client_accepted'],'| redis voters',d['redis_voters'],'| redis counts',d['redis_total'],'| pg rows',d['pg_rows'],'| queued',d['queued'])
print('E1 lost in PostgreSQL:',d['lost_in_pg'],'| voters marked but not counted:',d['gap_voters_vs_counts'],'| counted but not in PG:',d['gap_redis_vs_pg'],'| consistent',d['consistent'])
" | tee -a "$OUT/E1.txt"

# E2 graceful stop (SIGTERM) under load
read -r P O <<<"$(create_poll $PORT 2)"
node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode vote --connections 50 --duration 20 \
  --userStart 50000000 --label E2 --out "$OUT/E2-load.json" >/dev/null &
LP=$!
sleep 8; log "E2: graceful stop"; stop_app E TERM; wait $LP
ACC=$(python3 -c "import json;print(json.load(open('$OUT/E2-load.json'))['accepted_2xx'])")
start_app E $PORT $RPORT 5432; wait_healthy $PORT >/dev/null; wait_queue_drained $RPORT 60
python3 "$RT_DIR/scripts/consistency_check.py" $RPORT "$P" "$O" "$ACC" > "$OUT/E2-consistency.json"
python3 -c "import json;d=json.load(open('$OUT/E2-consistency.json'));print('E2 graceful: accepted',d['client_accepted'],'pg rows',d['pg_rows'],'lost_in_pg',d['lost_in_pg'],'consistent',d['consistent'])" | tee "$OUT/E2.txt"

stop_app E; stop_redis $RPORT; log "group E done"
