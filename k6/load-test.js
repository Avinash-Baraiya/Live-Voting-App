import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const voteSuccessCount = new Counter('vote_success_count');
const voteDuplicateCount = new Counter('vote_duplicate_count');
const voteErrorCount = new Counter('vote_error_count');
const voteSuccessRate = new Rate('vote_success_rate');
const voteLatency = new Trend('vote_latency_ms', true);

// Test configuration - choose scenario by setting SCENARIO env var:
// k6 run -e SCENARIO=smoke k6/load-test.js
// k6 run -e SCENARIO=load_1k k6/load-test.js
// k6 run -e SCENARIO=load_5k k6/load-test.js
// k6 run -e SCENARIO=load_10k k6/load-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const POLL_ID = __ENV.POLL_ID || '1';

const scenarios = {
  smoke: {
    executor: 'constant-vus',
    vus: 10,
    duration: '30s',
  },
  load_1k: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 1000 },
      { duration: '2m', target: 1000 },
      { duration: '30s', target: 0 },
    ],
  },
  load_5k: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: 5000 },
      { duration: '3m', target: 5000 },
      { duration: '1m', target: 0 },
    ],
  },
  load_10k: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '2m', target: 10000 },
      { duration: '5m', target: 10000 },
      { duration: '2m', target: 0 },
    ],
  },
};

const selectedScenario = __ENV.SCENARIO || 'smoke';

export const options = {
  scenarios: {
    voting: scenarios[selectedScenario],
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    vote_success_rate: ['rate>0.95'],
    http_req_failed: ['rate<0.05'],
  },
};

// Setup: create a poll before tests run
export function setup() {
  const payload = JSON.stringify({
    question: 'Who will win the IPL final?',
    options: ['CSK', 'MI', 'RCB', 'KKR'],
    expiresAt: '2027-12-31T23:59:59',
  });

  const res = http.post(`${BASE_URL}/poll`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, { 'poll created': (r) => r.status === 201 });

  const body = JSON.parse(res.body);
  const pollId = body.id;
  const optionIds = body.options.map((o) => o.id);

  console.log(`Created poll id=${pollId} with options: ${JSON.stringify(optionIds)}`);
  return { pollId, optionIds };
}

export default function (data) {
  const pollId = data.pollId || POLL_ID;
  const optionIds = data.optionIds || [1, 2, 3, 4];

  // Each virtual user has a unique userId based on VU id + iteration
  const userId = __VU * 100000 + __ITER;
  const optionId = optionIds[Math.floor(Math.random() * optionIds.length)];

  const payload = JSON.stringify({
    userId: userId,
    optionId: optionId,
  });

  const start = Date.now();
  const res = http.post(`${BASE_URL}/poll/${pollId}/vote`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'vote' },
  });
  const latency = Date.now() - start;
  voteLatency.add(latency);

  if (res.status === 201) {
    voteSuccessCount.add(1);
    voteSuccessRate.add(true);
  } else if (res.status === 409) {
    // Duplicate vote - expected for repeat VUs
    voteDuplicateCount.add(1);
    voteSuccessRate.add(true); // Duplicate handling is correct behavior
  } else {
    voteErrorCount.add(1);
    voteSuccessRate.add(false);
    console.error(`Unexpected error: status=${res.status}, body=${res.body}`);
  }

  check(res, {
    'vote accepted or duplicate': (r) => r.status === 201 || r.status === 409,
  });

  // Periodically check results
  if (__ITER % 100 === 0) {
    const resultsRes = http.get(`${BASE_URL}/poll/${pollId}/results`, {
      tags: { name: 'results' },
    });
    check(resultsRes, { 'results fetched': (r) => r.status === 200 });
  }

  sleep(0.01); // 10ms think time
}

export function teardown(data) {
  if (data && data.pollId) {
    const resultsRes = http.get(`${BASE_URL}/poll/${data.pollId}/results`);
    console.log(`Final results: ${resultsRes.body}`);
  }
}
