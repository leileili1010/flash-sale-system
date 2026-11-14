// Shared setup for the load tests: authenticate as the demo admin, schedule a dedicated drop,
// and mint one shopper token per virtual user through the benchmark-only helper.
import http from 'k6/http';
import { fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const RESULTS_DIR = __ENV.RESULTS_DIR || '/results';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function adminToken() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: __ENV.ADMIN_EMAIL || 'admin@fluffle.dev', password: __ENV.ADMIN_PASSWORD || 'admin-password' }),
    { headers: JSON_HEADERS });
  if (res.status !== 200) fail(`admin login failed: ${res.status} ${res.body}`);
  return res.json('accessToken');
}

/** Creates and publishes a drop that starts `startInSeconds` from now and ends shortly after the test. */
export function createDrop(token, { title, stock, startInSeconds, durationSeconds }) {
  const auth = { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
  const products = http.get(`${BASE_URL}/api/products`).json();
  const product = products.find((p) => p.sku === 'FF-MOSS-001') || products[0];
  const start = new Date(Date.now() + startInSeconds * 1000);
  const end = new Date(start.getTime() + durationSeconds * 1000);
  const res = http.post(`${BASE_URL}/api/admin/drops`, JSON.stringify({
    productId: product.id,
    title,
    dropPrice: 9.99,
    startTime: start.toISOString(),
    endTime: end.toISOString(),
    totalStock: stock,
    publish: true,
  }), auth);
  if (res.status !== 201) fail(`creating the drop failed: ${res.status} ${res.body}`);
  return { dropId: res.json('id'), startMs: start.getTime() };
}

/** One JWT per virtual user, minted in batches of 1,000. */
export function shopperTokens(run, count) {
  const tokens = [];
  for (let offset = 0; offset < count; offset += 1000) {
    const batch = Math.min(1000, count - offset);
    const res = http.post(`${BASE_URL}/api/demo/users?run=${run}&count=${batch}&offset=${offset}`, null, { timeout: '120s' });
    if (res.status !== 200) fail(`minting shoppers failed (is the backend running with the "benchmark" profile?): ${res.status}`);
    tokens.push(...res.json('tokens'));
  }
  return tokens;
}

export function runId(prefix) {
  return `${prefix}-${Date.now().toString(36)}`;
}

/** Plain-text summary of the counters and purchase latency, plus the full JSON for the report. */
export function summarize(name, data, extraLines = []) {
  const metric = (key) => (data.metrics[key] ? data.metrics[key].values : null);
  const count = (key) => (metric(key) ? metric(key).count : 0);
  const latency = metric('http_req_duration{name:purchase}') || metric('http_req_duration');
  const ms = (v) => (v === undefined ? 'n/a' : `${v.toFixed(1)} ms`);
  const lines = [
    `=== ${name} ===`,
    ...extraLines,
    `requests sent:        ${count('http_reqs')}`,
    `purchase latency:     p50 ${ms(latency['med'])} · p95 ${ms(latency['p(95)'])} · p99 ${ms(latency['p(99)'])} · max ${ms(latency['max'])}`,
  ];
  return {
    stdout: `${lines.join('\n')}\n`,
    [`${RESULTS_DIR}/${name}-summary.json`]: JSON.stringify(data, null, 2),
  };
}
