#!/usr/bin/env python3
"""Compare what Redis says about a poll with what PostgreSQL actually stored.

usage: consistency_check.py <redis_port> <poll_id> <option_ids comma-sep> [expected_accepted]

Reports, per poll:
  redis_counts   poll:{id}:option:{opt} counters (what /results shows)
  redis_voters   size of poll:{id}:voters (users marked as voted)
  queued         entries for this poll still waiting in vote:queue
  pg_counts      rows in the vote table per option
  pg_voters      distinct user_id rows in the vote table
and the gaps between them. If expected_accepted (HTTP 200s the client saw) is given,
it is compared too: accepted votes that never reached PostgreSQL are lost votes.
"""
import json
import subprocess
import sys

PSQL = "/opt/homebrew/opt/postgresql@14/bin/psql"


def redis(port, *args):
    out = subprocess.run(["redis-cli", "-p", str(port), *map(str, args)],
                         capture_output=True, text=True).stdout.strip()
    return out


def pg(sql, db="voting_test"):
    return subprocess.run([PSQL, "-d", db, "-Atc", sql], capture_output=True, text=True).stdout.strip()


def main():
    port, poll_id, opts = sys.argv[1], int(sys.argv[2]), [int(o) for o in sys.argv[3].split(",")]
    expected = int(sys.argv[4]) if len(sys.argv) > 4 else None

    redis_counts = {o: int(redis(port, "get", f"poll:{poll_id}:option:{o}") or 0) for o in opts}
    redis_voters = int(redis(port, "scard", f"poll:{poll_id}:voters") or 0)
    queue = redis(port, "lrange", "vote:queue", 0, -1).splitlines()
    queued = sum(1 for e in queue if e.startswith(f"{poll_id}:"))

    pg_counts = {o: 0 for o in opts}
    for line in pg(f"select option_id, count(*) from vote where poll_id={poll_id} group by option_id").splitlines():
        o, c = line.split("|")
        pg_counts[int(o)] = int(c)
    pg_voters = int(pg(f"select count(distinct user_id) from vote where poll_id={poll_id}") or 0)
    pg_rows = int(pg(f"select count(*) from vote where poll_id={poll_id}") or 0)

    r_total = sum(redis_counts.values())
    report = {
        "poll_id": poll_id,
        "redis_total": r_total,
        "redis_counts": redis_counts,
        "redis_voters": redis_voters,
        "queued": queued,
        "pg_rows": pg_rows,
        "pg_counts": pg_counts,
        "pg_voters": pg_voters,
        "gap_redis_vs_pg": r_total - pg_rows - queued,
        "gap_voters_vs_counts": redis_voters - r_total,
        "per_option_match": all(redis_counts[o] == pg_counts[o] for o in opts) and queued == 0,
    }
    if expected is not None:
        report["client_accepted"] = expected
        report["lost_in_pg"] = expected - pg_rows - queued
        report["lost_in_redis_counts"] = expected - r_total
    report["consistent"] = (report["gap_redis_vs_pg"] == 0 and report["gap_voters_vs_counts"] == 0
                            and (expected is None or (report["lost_in_pg"] == 0 and report["lost_in_redis_counts"] == 0)))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
