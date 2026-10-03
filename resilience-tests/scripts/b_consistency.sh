#!/usr/bin/env bash
# Group B — consistency. Stack B: app 8085, redis 6395, local voting_test DB.
source "$(dirname "$0")/lib.sh"
PORT=8085; RPORT=6395; OUT="$RESULTS_DIR/B"; mkdir -p "$OUT"
start_redis $RPORT; start_app B $PORT $RPORT 5432; log "B healthy after $(wait_healthy $PORT)s"
vote() { curl -s -o /dev/null -w '%{http_code}' -X POST "localhost:$PORT/poll/$1/vote" -H 'Content-Type: application/json' -d "$2"; }

# B2 double-click / retry storm: one user, 200 concurrent identical votes
read -r P O <<<"$(create_poll $PORT 2)"
node "$RT_DIR/scripts/load.js" --port $PORT --poll "$P" --options "$O" --mode same-user --user 777 --connections 50 --amount 200 \
  --label "B2 same user x200" --out "$OUT/B2-same-user.json" >/dev/null
wait_queue_drained $RPORT 60
echo "B2 status codes: $(python3 -c "import json;print(json.load(open('$OUT/B2-same-user.json'))['status_codes'])")" | tee "$OUT/B2.txt"
python3 "$RT_DIR/scripts/consistency_check.py" $RPORT "$P" "$O" 1 > "$OUT/B2-consistency.json"
echo "B2 consistent with exactly 1 accepted: $(python3 -c "import json;print(json.load(open('$OUT/B2-consistency.json'))['consistent'])")" | tee -a "$OUT/B2.txt"

# B4 invalid input
read -r P O <<<"$(create_poll $PORT 2)"; OPT1=${O%%,*}
read -r P2 O2 <<<"$(create_poll $PORT 2)"; OTHER=${O2%%,*}
{
  echo "case,expected,got"
  echo "option of another poll,400,$(vote $P "{\"userId\":1,\"optionId\":$OTHER}")"
  echo "unknown option,400,$(vote $P '{"userId":2,"optionId":999999999}')"
  echo "missing optionId,400,$(vote $P '{"userId":3}')"
  echo "missing userId,400,$(vote $P "{\"optionId\":$OPT1}")"
  echo "negative userId,400,$(vote $P "{\"userId\":-5,\"optionId\":$OPT1}")"
  echo "string userId,400,$(vote $P "{\"userId\":\"abc\",\"optionId\":$OPT1}")"
  echo "malformed JSON,400,$(vote $P '{userId:')"
  echo "empty body,400,$(vote $P '')"
  echo "unknown poll,404,$(vote 999999999 "{\"userId\":4,\"optionId\":$OPT1}")"
  echo "non-numeric pollId,400,$(curl -s -o /dev/null -w '%{http_code}' -X POST localhost:$PORT/poll/abc/vote -H 'Content-Type: application/json' -d '{"userId":5,"optionId":1}')"
  BIG=$(python3 -c "print('x'*2000000)")
  echo "2 MB body,413 or 400,$(curl -s -o /dev/null -w '%{http_code}' -X POST localhost:$PORT/poll/$P/vote -H 'Content-Type: application/json' --data-binary "{\"userId\":6,\"optionId\":$OPT1,\"pad\":\"$BIG\"}")"
  echo "create poll with no options,400,$(curl -s -o /dev/null -w '%{http_code}' -X POST localhost:$PORT/poll -H 'Content-Type: application/json' -d '{"question":"q","options":[]}')"
  echo "create poll missing question,400,$(curl -s -o /dev/null -w '%{http_code}' -X POST localhost:$PORT/poll -H 'Content-Type: application/json' -d '{"options":["a","b"]}')"
  echo "create poll expired in past,400,$(curl -s -o /dev/null -w '%{http_code}' -X POST localhost:$PORT/poll -H 'Content-Type: application/json' -d '{"question":"q","options":["a","b"],"expiresAt":"2000-01-01T00:00:00Z"}')"
} > "$OUT/B4-invalid-input.csv"
wait_queue_drained $RPORT 30
echo "B4 rows written for the bad-vote poll (expect 0): $(psql_test "select count(*) from vote where poll_id=$P")" | tee "$OUT/B4.txt"
column -s, -t "$OUT/B4-invalid-input.csv"

# B6 persistence lag: time from HTTP 200 to row visible in PostgreSQL
read -r P O <<<"$(create_poll $PORT 2)"; OPT1=${O%%,*}
echo "vote,lag_s" > "$OUT/B6-persistence-lag.csv"
for i in $(seq 1 10); do
  U=$((5000 + i)); T0=$(python3 -c 'import time;print(time.time())')
  vote "$P" "{\"userId\":$U,\"optionId\":$OPT1}" >/dev/null
  for _ in $(seq 1 300); do [[ "$(psql_test "select count(*) from vote where poll_id=$P and user_id=$U")" == "1" ]] && break; sleep 0.1; done
  echo "$i,$(python3 -c "import time;print(round(time.time()-$T0,2))")" >> "$OUT/B6-persistence-lag.csv"
  sleep 1.3
done
python3 -c "import csv;v=[float(r['lag_s']) for r in csv.DictReader(open('$OUT/B6-persistence-lag.csv'))];print(f'B6 lag min={min(v)}s avg={sum(v)/len(v):.2f}s max={max(v)}s')" | tee "$OUT/B6.txt"

# B5 expiry boundary: poll expires in 6 s, vote every 0.3 s for 14 s
EXP=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds=6)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
read -r P O < <(curl -s -X POST localhost:$PORT/poll -H 'Content-Type: application/json' -d "{\"question\":\"expiry\",\"options\":[\"a\",\"b\"],\"expiresAt\":\"$EXP\"}" | python3 -c 'import sys,json;b=json.load(sys.stdin);print(b["pollId"],",".join(str(o["optionId"]) for o in b["options"]))')
OPT1=${O%%,*}
echo "t_utc,code" > "$OUT/B5-expiry.csv"
END=$(( $(date +%s) + 14 )); U=8000
while (( $(date +%s) < END )); do
  echo "$(python3 -c 'import datetime;print(datetime.datetime.now(datetime.timezone.utc).strftime("%H:%M:%S.%f")[:-3])'),$(vote "$P" "{\"userId\":$U,\"optionId\":$OPT1}")" >> "$OUT/B5-expiry.csv"; U=$((U+1)); sleep 0.3
done
wait_queue_drained $RPORT 30
{
  echo "B5 expiresAt=$EXP"
  echo "B5 last accepted: $(grep ',200$' "$OUT/B5-expiry.csv" | tail -1)  first rejected: $(grep -v ',200$' "$OUT/B5-expiry.csv" | sed -n 2p)"
  echo "B5 rows stored after expiry time: $(psql_test "select count(*) from vote v join poll p on p.id=v.poll_id where v.poll_id=$P and v.created_at > p.expires_at")"
  echo "B5 poll status now: $(psql_test "select status from poll where id=$P")"
} | tee "$OUT/B5.txt"

# B3 duplicate vote across a Redis crash (voter set lost)
read -r P O <<<"$(create_poll $PORT 2)"; OPT1=${O%%,*}
C1=$(vote "$P" "{\"userId\":4242,\"optionId\":$OPT1}")
wait_queue_drained $RPORT 30
kill -9 "$(cat "$RUN_DIR/redis-$RPORT/redis.pid")"; sleep 1; start_redis $RPORT; sleep 3
C2=$(vote "$P" "{\"userId\":4242,\"optionId\":$OPT1}")
sleep 12
{
  echo "B3 first vote: $C1, same user again after Redis crash+restart: $C2 (should be 409)"
  echo "B3 rows in PostgreSQL for that user (unique constraint): $(psql_test "select count(*) from vote where poll_id=$P and user_id=4242")"
  echo "B3 vote:queue length 12 s later (stuck if >0): $(queue_len $RPORT)"
  echo "B3 results shown now: $(curl -s localhost:$PORT/poll/$P/results)"
  echo "B3 flush errors in app log: $(grep -c 'Failed to persist' "$RUN_DIR/app-B.log")"
} | tee "$OUT/B3.txt"
sleep 15
echo "B3 queue length 27 s later: $(queue_len $RPORT); flush errors: $(grep -c 'Failed to persist' "$RUN_DIR/app-B.log")" | tee -a "$OUT/B3.txt"

stop_app B; stop_redis $RPORT; log "group B done"
