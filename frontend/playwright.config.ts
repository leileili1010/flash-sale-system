import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the real stack. Start the infrastructure, backend and frontend first:
 *   docker compose -f infra/docker-compose.yml up -d --wait
 *   (cd backend && ./mvnw spring-boot:run)
 *   (cd frontend && npm run dev)
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
});
