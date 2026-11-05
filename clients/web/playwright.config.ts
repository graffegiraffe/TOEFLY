import { defineConfig, devices } from '@playwright/test';

// End-to-end scenarios run against the local stack: infra/compose, api-gateway, profile-service and `pnpm dev`.
// Keycloak sends mail to Mailpit, whose API the tests read.
export default defineConfig({
  testDir: 'e2e',
  timeout: 90_000,
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://app.toefly.localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  // E2E_CHANNEL=chrome uses the installed Google Chrome instead of the downloaded Chromium.
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], channel: process.env.E2E_CHANNEL } }],
});
