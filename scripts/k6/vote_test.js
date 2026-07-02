import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

export let errorRate = new Rate('errors');

export let options = {
  scenarios: {
    votes: {
      executor: 'constant-arrival-rate',
      rate: __ENV.RATE ? parseInt(__ENV.RATE) : 1000, // requests per second
      timeUnit: '1s',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: __ENV.PREALLOC_VUS ? parseInt(__ENV.PREALLOC_VUS) : 400,
      maxVUs: __ENV.MAX_VUS ? parseInt(__ENV.MAX_VUS) : 2000,
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed': ['rate<0.02'],
    'errors': ['rate<0.02'],
  },
};

const BASE = __ENV.BASE || 'http://localhost:8080';

export function setup() {
  const payload = JSON.stringify({
    question: 'Load test poll - pick an option',
    options: ['A', 'B', 'C', 'D'],
    expiresAt: '2099-12-31T23:59:59Z'
  });
  const r = http.post(`${BASE}/poll`, payload, { headers: { 'Content-Type': 'application/json' } });
  let pollId = 1;
  try {
    const body = JSON.parse(r.body || '{}');
    if (body && body.pollId) pollId = body.pollId;
  } catch (e) {
    // ignore
  }
  return { pollId };
}

export default function (data) {
  // simulate many distinct users
  const userId = Math.floor(Math.random() * 1e9);
  const optionId = Math.floor(Math.random() * 4) + 1;

  const body = JSON.stringify({ userId: userId, optionId: optionId });
  const res = http.post(`${BASE}/poll/${data.pollId}/vote`, body, { headers: { 'Content-Type': 'application/json' } });

  const ok = check(res, {
    'status is 200 or 409': (r) => r.status === 200 || r.status === 409
  });

  if (!ok) errorRate.add(1);
}
