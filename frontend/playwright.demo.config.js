import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './tests/demo',
  outputDir: './test-results-demo',
  workers: 1,
  use: { baseURL: 'http://127.0.0.1:4175', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run build:demo && npm run preview:demo -- --host 127.0.0.1 --strictPort',
    url: 'http://127.0.0.1:4175',
    reuseExistingServer: false,
  },
})
