import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, createProject, ids } from './support/projects';

test('项目筛选支持多省单省多市，清空无效地市并恢复 URL', async ({ page }) => {
  await page.goto('/projects');
  await chooseActor(page, ids.admin);
  const province = page.locator('summary[aria-label="归属省分"]');
  const city = page.locator('summary[aria-label="归属地市／区县"]');
  const cityDisabled = page.getByRole('button', { name: '归属地市／区县', exact: true });
  await expect(cityDisabled).toBeDisabled();
  await expect(page.getByPlaceholder('输入项目编号')).toBeHidden();
  await page.getByText('更多筛选条件', { exact: true }).click();
  await expect(page.getByPlaceholder('输入项目编号')).toBeVisible();

  await province.click();
  const provinces = page.getByRole('group', { name: '归属省分', exact: true });
  const guangdong = provinces.getByRole('checkbox', { name: '广东省', exact: true });
  await guangdong.check();
  await guangdong.press('Escape');
  await city.click();
  const cities = page.getByRole('group', { name: '归属地市／区县', exact: true });
  const guangzhou = cities.getByRole('checkbox', { name: '广州市', exact: true });
  const shenzhen = cities.getByRole('checkbox', { name: '深圳市', exact: true });
  await guangzhou.check();
  await shenzhen.check();
  await shenzhen.press('Escape');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL((url) => url.searchParams.getAll('cityIds').length === 2);
  await page.reload();
  await expect(city).toHaveText('已选 2 项');

  await province.click();
  const other = provinces.getByRole('checkbox', { name: '江西省', exact: true });
  await other.check();
  await other.press('Escape');
  await expect(cityDisabled).toBeDisabled();
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL(
    (url) =>
      url.searchParams.getAll('provinceIds').length === 2 && !url.searchParams.has('cityIds'),
  );
  const exporting = page.waitForResponse(
    (response) =>
      response.url().endsWith('/projects/exports') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
  await page.getByRole('button', { name: '生成导出文件', exact: true }).click();
  const exportResponse = await exporting;
  expect(exportResponse.ok(), await exportResponse.text()).toBeTruthy();
  const exportBody = exportResponse.request().postDataJSON();
  expect(exportBody.provinceIds).toHaveLength(2);
  expect(exportBody).not.toHaveProperty('cityIds');
  await province.click();
  await guangdong.uncheck();
  await guangdong.press('Escape');
  await city.click();
  await expect(cities.getByRole('checkbox', { checked: true })).toHaveCount(0);
  await expect(guangzhou).toHaveCount(0);
});

test('省分分布与地图等高，只有超出表格内容滚动', async ({ page, request }) => {
  await createProject(request, `分布验收-${randomUUID()}`);
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const map = page.getByRole('region', { name: '驾驶舱项目区域分布', exact: true });
  const ranking = page.getByRole('region', { name: '省分分布', exact: true });
  const tableArea = ranking.getByRole('region', { name: '省分分布表格' });
  await expect(map).toBeVisible();
  await expect(tableArea.locator('tbody tr').first()).toBeVisible();
  const height = () => ranking.evaluate((element) => element.getBoundingClientRect().height);
  const mapHeight = await map.evaluate((element) => element.getBoundingClientRect().height);
  expect(await height()).toBeCloseTo(mapHeight, 0);
  expect(await tableArea.evaluate((element) => element.scrollHeight)).toBe(
    await tableArea.evaluate((element) => element.clientHeight),
  );
  await tableArea.locator('tbody').evaluate((body) => {
    for (let i = 0; i < 35; i++) body.append(body.firstElementChild!.cloneNode(true));
  });
  expect(await height()).toBeCloseTo(mapHeight, 0);
  expect(
    await tableArea.evaluate((element) => {
      element.scrollTop = 100;
      return element.scrollTop;
    }),
  ).toBeGreaterThan(0);
});
