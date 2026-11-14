// Baseline: the same spike against the deliberately naive read-then-write endpoint (the reference
// project's original approach), to show why the real design is needed.
//
//   docker run --rm -v "$PWD/load-tests:/scripts" -v "$PWD/load-tests/results:/results" \
//     -e SHOPPERS=2000 -e STOCK=100 grafana/k6:1.8.2 run /scripts/naive.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { adminToken, BASE_URL, createDrop, summarize } from './lib/setup.js';

const SHOPPERS = Number(__ENV.SHOPPERS || 2000);
const STOCK = Number(__ENV.STOCK || 100);

const accepted = new Counter('purchases_accepted');
const soldOut = new Counter('purchases_sold_out');
const otherErrors = new Counter('purchases_other_errors');
const connectionErrors = new Counter('purchases_connection_errors');

export const options = {
  setupTimeout: '60s',
  scenarios: {
    release: { executor: 'per-vu-iterations', vus: SHOPPERS, iterations: 1, maxDuration: '3m' },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const admin = adminToken();
  return createDrop(admin, { title: `Load test · naive ${SHOPPERS}x${STOCK}`, stock: STOCK, startInSeconds: 10, durationSeconds: 120 });
}

export default function (data) {
  const waitMs = data.startMs - Date.now();
  if (waitMs > 0) sleep(waitMs / 1000);
  const res = http.post(`${BASE_URL}/api/demo/naive-purchase/${data.dropId}`, null, { tags: { name: 'purchase' } });
  if (res.status === 0) connectionErrors.add(1);
  else if (res.status === 200) accepted.add(1);
  else if (res.status === 409) soldOut.add(1);
  else otherErrors.add(1);
}

export function teardown(data) {
  const result = http.get(`${BASE_URL}/api/demo/naive-purchase/${data.dropId}`).json();
  console.log(`naive drop ${data.dropId}: ${result.acceptedPurchases} purchases accepted for ${result.totalStock} units (oversold by ${result.oversold})`);
  // This check is expected to FAIL: it demonstrates the overselling the real design prevents.
  check(result, { 'no oversell': (r) => r.oversold === 0 });
}

export function handleSummary(data) {
  const c = (key) => (data.metrics[key] ? data.metrics[key].values.count : 0);
  const oversell = data.metrics['checks{check:no oversell}'] || data.metrics.checks;
  return summarize(`naive-${SHOPPERS}x${STOCK}`, data, [
    `shoppers / stock:     ${SHOPPERS} / ${STOCK}`,
    `accepted (200):       ${c('purchases_accepted')}   <- compare with stock`,
    `sold out (409):       ${c('purchases_sold_out')}`,
    `other errors:         ${c('purchases_other_errors')}`,
    `connection errors:    ${c('purchases_connection_errors')}`,
    `oversell check:       ${oversell && oversell.values.rate === 1 ? 'PASS' : 'FAIL (expected)'}`,
  ]);
}
