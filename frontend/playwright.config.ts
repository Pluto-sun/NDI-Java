import { defineConfig } from '@playwright/test';

if (process.env.NDI_E2E_ENABLED !== '1') {
  throw new Error('请通过 scripts/run-project-e2e.sh 使用隔离数据库运行浏览器验收。');
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  outputDir: '../tmp/e2e-results',
  reporter: [['list'], ['html', { outputFolder: '../tmp/e2e-report', open: 'never' }]],
  use: {
    actionTimeout: 10_000,
    baseURL: 'http://127.0.0.1:15173',
    browserName: 'chromium',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    {
      name: 'small-screen',
      use: { viewport: { width: 375, height: 812 }, reducedMotion: 'reduce' },
    },
  ],
  webServer: {
    command: 'npm run dev -- --port 15173 --strictPort',
    url: 'http://127.0.0.1:15173',
    reuseExistingServer: false,
    env: { NDI_DEV_API_TARGET: 'http://127.0.0.1:18080' },
  },
});
