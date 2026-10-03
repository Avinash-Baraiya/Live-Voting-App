#!/usr/bin/env bash
# Phase 1 — load. Needs stack A running (app 8081, redis 6391, local voting_test DB).
# A1 baseline (1 connection), A2 ramp (10..500 connections, 30 s each), queue backlog after each stage.
source "$(dirname "$0")/lib.sh"
PORT=8081; RPORT=6391
OUT="$RESULTS_DIR/phase1"; mkdir -p "$OUT"

read -r P O <<<"$(create_poll $PORT 4)"
log "A1/A2 poll=$P options=$O"
USER=10000000

log "A1 baseline: 1 connection, 500 votes"
node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode vote --connections 1 --amount 500 \
  --userStart $USER --label "A1 baseline c=1" --out "$OUT/A1-vote-c1.json" >/dev/null
USER=$((USER + 1000))
node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode results --connections 1 --amount 500 \
  --label "A1 results c=1" --out "$OUT/A1-results-c1.json" >/dev/null
wait_queue_drained $RPORT 60

echo "stage,connections,rps,p50,p90,p99,max,accepted,non2xx,errors,timeouts,queue_after,queue_after_10s" > "$OUT/A2-ramp.csv"
for C in 10 50 100 200 500; do
  log "A2 ramp: $C connections, 30 s"
  probe $PORT "$P" "${O%%,*}" "$OUT/A2-probe-c$C.csv" 28 $((USER + 9000000)) &
  PP=$!
  node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode vote --connections $C --duration 30 \
    --userStart $USER --label "A2 c=$C" --out "$OUT/A2-vote-c$C.json" >/dev/null
  wait $PP
  python3 -c "import csv;v=sorted(float(r['vote_s'])*1000 for r in csv.DictReader(open('$OUT/A2-probe-c$C.csv')) if r['vote_code']=='200');n=len(v);print(f'  probe (real user) vote latency ms: n={n} p50={v[n//2]:.0f} p90={v[int(n*.9)]:.0f} max={v[-1]:.0f}' if n else '  probe: no successful votes')" | tee -a "$OUT/A2-probe-summary.txt"
  Q1=$(queue_len $RPORT); sleep 10; Q2=$(queue_len $RPORT)
  python3 - "$OUT/A2-vote-c$C.json" "$C" "$Q1" "$Q2" >> "$OUT/A2-ramp.csv" <<'EOF'
import json,sys
d=json.load(open(sys.argv[1])); l=d["latency_ms"]
print(f"A2,{sys.argv[2]},{d['rps_avg']:.0f},{l['p50']},{l['p90']},{l['p99']},{l['max']},{d['accepted_2xx']},{d['non_2xx']},{d['errors']},{d['timeouts']},{sys.argv[3]},{sys.argv[4]}")
EOF
  USER=$((USER + 10000000))
  log "  queue backlog: $Q1 right after, $Q2 after 10 s (drain rate $(( (Q1 - Q2) / 10 ))/s)"
done

Q=$(queue_len $RPORT); R1=$(queue_len $RPORT); sleep 20; R2=$(queue_len $RPORT)
RATE=$(( (R1 - R2) / 20 )); echo "backlog=$Q drain_rate=${RATE}/s projected_catch_up_min=$(( Q / (RATE>0?RATE:1) / 60 ))" | tee "$OUT/A2-backlog.txt"
column -s, -t "$OUT/A2-ramp.csv"
