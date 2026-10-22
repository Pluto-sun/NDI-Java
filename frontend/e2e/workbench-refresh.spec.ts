import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, ids } from './support/projects';

test('当前工作台定时刷新且失败保留旧值，隐藏及离开页面不再轮询历史报表', async ({
  page,
  request,
}, testInfo) => {
  await page.clock.install();
  let overviewRequests = 0;
  let historyRequests = 0;
  let fail = true;
  let forbidden = false;
  await page.route('**/workbench/overview', async (route) => {
    overviewRequests += 1;
    if (forbidden) {
      await route.fulfill({
        status: 403,
        contentType: 'application/problem+json',
        body: JSON.stringify({ code: 'PROJECT_ACCESS_FORBIDDEN', detail: '读取权限已撤销' }),
      });
      return;
    }
    if (fail) await route.fulfill({ status: 503, body: '{}' });
    else await route.continue();
  });
  page.on('request', (req) => {
    if (/\/workbench\/(history|stage-trend)\?/.test(req.url())) historyRequests += 1;
  });
  await page.goto('/workbench');
  await chooseActor(page, ids.leader);
  await expect(page.getByRole('alert')).toBeVisible();
  fail = false;
  const refresh = page.getByRole('button', { name: '刷新当前概览', exact: true });
  await refresh.click();
  const count = page.locator('.kpi').filter({ hasText: '项目总数' }).locator('strong');
  await expect(count).toBeVisible();
  await page.getByLabel('趋势起始月份', { exact: true }).fill('2020-01');
  await page.getByLabel('报表／趋势结束月份', { exact: true }).fill('2020-01');
  await page.getByRole('button', { name: '查看月报与趋势', exact: true }).click();
  const historicalCount = page
    .locator('.kpi')
    .filter({ hasText: '2020-01 项目数' })
    .locator('strong');
  await expect(historicalCount).toHaveText('2');
  const historicalCalls = historyRequests;
  expect(historicalCalls).toBeGreaterThan(0);
  const before = Number(await count.textContent());
  const name = `自动刷新-${randomUUID()}`;
  await createProject(request, name);
  await page.clock.fastForward(30_000);
  await expect(count).toHaveText(String(before + 1));
  const activities = page.getByRole('region', { name: '近期项目变动', exact: true });
  await expect(activities.getByRole('link', { name, exact: true })).toBeVisible();
  fail = true;
  await page.clock.fastForward(30_000);
  await expect(page.getByRole('alert')).toContainText('当前保留上次成功结果');
  await expect(count).toHaveText(String(before + 1));
  fail = false;
  await refresh.click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await page.evaluate(() =>
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' }),
  );
  const calls = overviewRequests;
  await page.clock.fastForward(60_000);
  expect(overviewRequests).toBe(calls);
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await expect.poll(() => overviewRequests).toBe(calls + 1);
  await expect(refresh).toBeEnabled();
  expect(historyRequests).toBe(historicalCalls);
  await expect(historicalCount).toHaveText('2');
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath('workbench-live-refresh.png'),
    fullPage: false,
  });
  forbidden = true;
  await refresh.click();
  await expect(page.getByRole('alert')).toContainText('读取权限已撤销');
  await expect(count).toHaveCount(0);
  await expect(activities).toHaveCount(0);
  await page.getByRole('link', { name: '查看项目清单', exact: true }).click();
  await expect(page).toHaveURL(/\/projects$/);
  await expect(page.getByRole('heading', { name: '项目列表', exact: true })).toBeVisible();
  const ended = overviewRequests;
  await page.clock.fastForward(60_000);
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  expect(overviewRequests).toBe(ended);
});

test('概览刷新未结束时不叠加请求，离开后迟到响应不覆盖新页面', async ({ page }) => {
  await page.clock.install();
  await page.goto('/workbench');
  await chooseActor(page, ids.reporter);
  const refresh = page.getByRole('button', { name: '刷新当前概览', exact: true });
  await expect(refresh).toBeEnabled();
  let calls = 0;
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/workbench/overview', async (route) => {
    calls += 1;
    const response = await route.fetch();
    await gate;
    await route.fulfill({ response }).catch(() => {});
  });
  await refresh.click();
  await expect(page.getByRole('button', { name: '概览刷新中…', exact: true })).toBeDisabled();
  await expect.poll(() => calls).toBe(1);
  await page.clock.fastForward(90_000);
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  expect(calls).toBe(1);
  await page.getByRole('link', { name: '查看项目清单', exact: true }).click();
  release();
  await expect(page).toHaveURL(/\/projects$/);
  await expect(page.getByRole('heading', { name: '项目列表', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '刷新当前概览', exact: true })).toHaveCount(0);
});
