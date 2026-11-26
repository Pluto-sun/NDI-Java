import { expect, test } from '@playwright/test';
import { importWorkbook } from './support/importWorkbook';
import { chooseActor, ids } from './support/projects';

test('导入遮罩阻止连点及键盘，错误后恢复，离开页面时卸载', async ({ page }, info) => {
  await page.goto('/scheduling/import');
  await chooseActor(page, ids.admin);
  const stage = page.getByRole('dialog', { name: '正在处理' });
  let requests = 0;
  let release!: () => void;
  const paused = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/production/plans/imports/task', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    requests++;
    await paused;
    await route.fulfill({ status: 400, json: { message: '验收业务错误' } });
  });
  const workbook = importWorkbook([{ 项目名称: '遮罩验收' }]);
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '遮罩验收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: workbook,
  });
  await expect(stage.getByRole('status')).toHaveText('正在上传 Excel 并识别表头…');
  await expect(stage.locator('img')).toHaveJSProperty('complete', true);
  for (const key of ['Tab', 'Enter', 'Escape']) await page.keyboard.press(key);
  await page.mouse.dblclick(12, 12);
  await stage.getByRole('status').dblclick();
  expect(await page.evaluate(() => window.getSelection()?.toString())).toBe('');
  await expect(stage).toBeVisible();
  expect(requests).toBe(1);
  expect(await stage.evaluate((dialog) => dialog.contains(document.activeElement))).toBe(true);
  if (info.project.name === 'small-screen') {
    expect(
      await stage
        .locator('.blocking-operation-logo')
        .evaluate((icon) => getComputedStyle(icon, '::after').animationName),
    ).toBe('none');
  }
  await page.screenshot({ path: info.outputPath('import-blocking-overlay.png') });
  release();
  await expect(stage).toHaveCount(0);
  await expect(page.getByText('验收业务错误')).toBeVisible();
  await page.unroute('**/production/plans/imports/task');

  await page.route('**/production/plans/imports/task', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    await route.abort('timedout');
  });
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '超时验收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: workbook,
  });
  await expect(page.getByText(/无法连接 Java 服务/)).toBeVisible();
  await expect(stage).toHaveCount(0);
  await page.unroute('**/production/plans/imports/task');

  let leave!: () => void;
  const pending = new Promise<void>((resolve) => {
    leave = resolve;
  });
  await page.route('**/production/plans/imports/task', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    await pending;
    await route.abort().catch(() => {});
  });
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '离页验收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: workbook,
  });
  await expect(stage).toBeVisible();
  await page.goto('/scheduling');
  leave();
  await expect(stage).toHaveCount(0);
});
