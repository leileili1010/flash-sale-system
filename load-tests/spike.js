// Spike test: SHOPPERS authenticated users all press "Buy" for a STOCK-unit drop at the exact
// moment it opens. Verifies that exactly STOCK orders exist afterwards and nothing is oversold.
//
//   docker run --rm -v "$PWD/load-tests:/scripts" -v "$PWD/load-tests/results:/results" \
//     -e SHOPPERS=2000 -e STOCK=100 grafana/k6:1.8.2 run /scripts/spike.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { adminToken, BASE_URL, createDrop, runId, shopperTokens, summarize } from './lib/setup.js';

const SHOPPERS = Number(__ENV.SHOPPERS || 2000);
const STOCK = Number(__ENV.STOCK || 100);

const accepted = new Counter('purchases_accepted');
const soldOut = new Counter('purchases_sold_out');
const rateLimited = new Counter('purchases_rate_limited');
const otherErrors = new Counter('purchases_other_errors');
const connectionErrors = new Counter('purchases_connection_errors');

export const options = {
  setupTimeout: '180s',
  teardownTimeout: '90s',
  scenarios: {
    release: { executor: 'per-vu-iterations', vus: SHOPPERS, iterations: 1, maxDuration: '3m' },
  },
  thresholds: {
    'http_req_duration{name:purchase}': ['p(95)<1000'],
    'checks{check:no oversell}': ['rate==1'],
    'checks{check:every accepted purchase became an order}': ['rate==1'],
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const admin = adminToken();
  const tokens = shopperTokens(runId('spike'), SHOPPERS);
  // Start after the tokens are minted so every virtual user is waiting at the starting line.
  const drop = createDrop(admin, { title: `Load test · spike ${SHOPPERS}x${STOCK}`, stock: STOCK, startInSeconds: 20, durationSeconds: 180 });
  return { ...drop, tokens, admin };
}

export default function (data) {
  const token = data.tokens[__VU - 1];
  const waitMs = data.startMs - Date.now();
  if (waitMs > 0) sleep(waitMs / 1000);

  const res = http.post(`${BASE_URL}/api/drops/${data.dropId}/purchase`, null, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'purchase' },
  });
  let code;
  if (res.status === 0) code = 'CONNECTION_ERROR';
  else if (res.status === 202) code = 'ACCEPTED';
  else code = (res.body && res.json('code')) || `HTTP_${res.status}`;
  if (code === 'CONNECTION_ERROR') connectionErrors.add(1);
  else if (code === 'ACCEPTED') accepted.add(1);
  else if (code === 'SOLD_OUT') soldOut.add(1);
  else if (code === 'RATE_LIMITED') rateLimited.add(1);
  else otherErrors.add(1);
  check(res, { 'expected response (202, 409 sold out, 429)': () => ['ACCEPTED', 'SOLD_OUT', 'RATE_LIMITED'].includes(code) });
}

export function teardown(data) {
  const auth = { headers: { Authorization: `Bearer ${data.admin}` } };
  // Wait for Kafka to drain: every reservation must turn into a persisted order.
  let drop;
  for (let i = 0; i < 60; i++) {
    const report = http.get(`${BASE_URL}/api/admin/inventory`, auth).json().find((r) => r.dropId === data.dropId);
    drop = http.get(`${BASE_URL}/api/admin/drops`, auth).json().find((d) => d.id === data.dropId);
    if (report && report.inFlightReservations === 0) break;
    sleep(1);
  }
  const orders = drop.lockedStock + drop.soldStock;
  console.log(`drop ${data.dropId}: available=${drop.availableStock} locked=${drop.lockedStock} sold=${drop.soldStock} of ${drop.totalStock}`);
  check(drop, {
    'no oversell': () => orders <= drop.totalStock && drop.availableStock >= 0,
    'every accepted purchase became an order': () => orders === Math.min(STOCK, SHOPPERS),
  });
}

export function handleSummary(data) {
  const c = (key) => (data.metrics[key] ? data.metrics[key].values.count : 0);
  return summarize(`spike-${SHOPPERS}x${STOCK}`, data, [
    `shoppers / stock:     ${SHOPPERS} / ${STOCK}`,
    `accepted (202):       ${c('purchases_accepted')}`,
    `sold out (409):       ${c('purchases_sold_out')}`,
    `rate limited (429):   ${c('purchases_rate_limited')}`,
    `other errors:         ${c('purchases_other_errors')}`,
    `connection errors:    ${c('purchases_connection_errors')}`,
    `oversell check:       ${data.metrics['checks{check:no oversell}'] ? (data.metrics['checks{check:no oversell}'].values.rate === 1 ? 'PASS' : 'FAIL') : 'n/a'}`,
  ]);
}
