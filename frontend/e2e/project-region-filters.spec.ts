import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, createProject, ids } from './support/projects';

test('项目筛选先选省分，变更省分时清空地市', async ({ page }) => {
  await page.goto('/projects');
  await chooseActor(page, ids.admin);
  const province = page.getByRole('combobox', { name: '归属省分', exact: true });
  const city = page.getByRole('combobox', { name: '归属地市／区县', exact: true });
  await expect(city).toBeDisabled();
  await expect(city).toHaveValue('');
  await expect(page.getByPlaceholder('输入项目编号')).toBeHidden();
  await page.getByText('更多筛选条件', { exact: true }).click();
  await expect(page.getByPlaceholder('输入项目编号')).toBeVisible();

  await province.selectOption(ids.province);
  await expect(city).toBeEnabled();
  await expect(city.locator('option').filter({ hasText: '广州市' })).toHaveCount(0);
  await city.selectOption(ids.city);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL(
    (url) =>
      url.searchParams.get('provinceId') === ids.province &&
      url.searchParams.get('cityId') === ids.city,
  );

  await province.click();
  await page
    .getByRole('listbox', { name: '可选项' })
    .getByRole('option', { name: '广东省', exact: true })
    .click();
  await expect(province.locator('option:checked')).toHaveText('广东省');
  await expect(city).toHaveValue('');
  await expect(city.locator('option').filter({ hasText: '广州市' })).toHaveCount(1);
  await expect(city.locator(`option[value="${ids.city}"]`)).toHaveCount(0);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL(
    (url) => !!url.searchParams.get('provinceId') && !url.searchParams.has('cityId'),
  );
  await province.click();
  await page
    .getByRole('listbox', { name: '可选项' })
    .getByRole('option', { name: '全部省分', exact: true })
    .click();
  await expect(city).toBeDisabled();
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
