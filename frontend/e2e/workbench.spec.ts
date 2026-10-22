import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { ids, chooseActor, createProject } from './support/projects';

test('工作台地图键盘缩放、区域筛选、六项指标与动态项目跳转', async ({
  page,
  request,
}, testInfo) => {
  const name = `动态-${randomUUID()}`;
  const project = await createProject(request, name);
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const map = page.getByRole('region', { name: '项目区域分布', exact: true });
  const canvas = map.getByRole('group', { name: '项目区域分布地图', exact: true });
  await expect(canvas).toBeVisible();
  const initial = await canvas.getAttribute('viewBox');
  await map.getByRole('button', { name: '放大地图', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(canvas).not.toHaveAttribute('viewBox', initial!);
  await map.getByRole('button', { name: '地图右移', exact: true }).click();
  await map.getByRole('button', { name: '复位', exact: true }).click();
  await expect(canvas).toHaveAttribute('viewBox', initial!);
  const metric = map.getByRole('combobox', { name: '着色与排行指标', exact: true });
  for (const code of ['projectCount', 'expectedAmount', 'network', 'gewu', 'bigData', 'wangwu']) {
    await metric.selectOption(code);
    await expect(metric).toHaveValue(code);
  }
  await map.getByText(/^筛选区域（/).click();
  await map.getByRole('button', { name: '清空', exact: true }).click();
  await expect(map.getByText('请选择至少一个区域。', { exact: true })).toBeVisible();
  await map.getByRole('button', { name: '全选', exact: true }).click();
  await expect(map.getByText('请选择至少一个区域。', { exact: true })).toHaveCount(0);
  const activity = page.getByRole('region', { name: '近期项目变动', exact: true });
  await expect(activity.getByRole('link', { name, exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('workbench-current.png'), fullPage: true });
  await activity.getByRole('link', { name, exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/projects/${project.id}$`));
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
});
