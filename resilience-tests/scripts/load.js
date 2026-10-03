#!/usr/bin/env node
// Load generator for the voting API (autocannon, programmatic so every vote gets its own userId).
//
// usage: node load.js --port 8081 --poll 1 --options 1,2 --mode vote --connections 50 --duration 30 \
//                     [--amount N] [--userStart 1000000] [--out results/a2.json] [--label "A2 c=50"]
// modes:
//   vote       POST /poll/{id}/vote, unique userId per request (real-world traffic)
//   same-user  POST /poll/{id}/vote, one userId for every request (double-click / retry storm)
//   results    GET  /poll/{id}/results
//   mixed      1 vote : 9 results reads
const autocannon = require('autocannon');
const fs = require('fs');

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, arr) => {
  if (a.startsWith('--')) acc.push([a.slice(2), arr[i + 1]]);
  return acc;
}, []));

const port = args.port || '8081';
const pollId = args.poll;
const options = (args.options || '').split(',').map(Number);
const mode = args.mode || 'vote';
const connections = Number(args.connections || 10);
const duration = args.duration ? Number(args.duration) : undefined;
const amount = args.amount ? Number(args.amount) : undefined;
let userId = Number(args.userStart || Date.now() % 1e9);

const votePath = `/poll/${pollId}/vote`;
const voteReq = (fixedUser) => ({
  method: 'POST',
  path: votePath,
  headers: { 'content-type': 'application/json' },
  setupRequest: (req) => {
    const u = fixedUser ?? userId++;
    req.body = JSON.stringify({ userId: u, optionId: options[u % options.length] });
    return req;
  },
});
const resultsReq = { method: 'GET', path: `/poll/${pollId}/results` };

let requests;
if (mode === 'vote') requests = [voteReq()];
else if (mode === 'same-user') requests = [voteReq(Number(args.user || 424242))];
else if (mode === 'results') requests = [resultsReq];
else if (mode === 'mixed') requests = [voteReq(), ...Array(9).fill(resultsReq)];
else throw new Error(`unknown mode ${mode}`);

const opts = { url: `http://localhost:${port}`, connections, timeout: 30, requests };
if (amount) opts.amount = amount; else opts.duration = duration || 10;

const inst = autocannon(opts, (err, r) => {
  if (err) { console.error(err); process.exit(1); }
  const codes = Object.fromEntries(Object.entries(r.statusCodeStats || {}).map(([k, v]) => [k, v.count]));
  const summary = {
    label: args.label || mode,
    mode, connections, duration: r.duration, pollId,
    requests_total: r.requests.total,
    rps_avg: r.requests.average,
    latency_ms: { p50: r.latency.p50, p90: r.latency.p90, p99: r.latency.p99, max: r.latency.max, avg: r.latency.average },
    status_codes: codes,
    accepted_2xx: r['2xx'],
    non_2xx: r.non2xx,
    errors: r.errors,
    timeouts: r.timeouts,
    first_user: Number(args.userStart || 0),
    next_user: userId,
  };
  console.log(JSON.stringify(summary, null, 2));
  if (args.out) fs.writeFileSync(args.out, JSON.stringify(summary, null, 2));
});
process.once('SIGINT', () => inst.stop());
