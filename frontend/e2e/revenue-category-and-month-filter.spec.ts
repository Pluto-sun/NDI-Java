import { chooseListOptions } from './support/listFilters';
import { requiredRevenueFields } from './support/revenue';
import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, ids } from './support/projects';

test('排产和计收筛选月可选择、清空并恢复', async ({ page }) => {
  for (const [path, label] of [
    ['/scheduling', '排产月份'],
    ['/revenue', '费用账期'],
  ]) {
    await page.goto(path);
    if (path === '/scheduling') await chooseActor(page, ids.admin);
    const form = page.getByRole('form', { name: path === '/scheduling' ? '排产筛选' : '计收筛选' });
    const month = form.getByLabel(label, { exact: true });
    await chooseListOptions(page, label, ['2026-10']);
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page).toHaveURL(/month=2026-10/);
    await page.reload();
    await expect(month).toHaveText('2026-10');
    await chooseListOptions(page, label, [], true);
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page).not.toHaveURL(/month=/);
  }
});

test('计收分类和现有筛选组合，明细与汇总一致，详情返回恢复', async ({ page, request }) => {
  const root = 'http://127.0.0.1:18080/api/v1/revenue/entries';
  const projectName = '分类测试-' + randomUUID();
  for (const [category, accountMonth, feeMonth] of [
    ['数字化', '2026-10', '2026-09'],
    ['智能化', '2026-10', '2026-09'],
    ['智能化', '2026-09', '2026-10'],
  ]) {
    const source = {
      ...requiredRevenueFields,
      月账期: accountMonth,
      费用账期: feeMonth,
      项目名称: projectName + category + feeMonth,
      合同编号: projectName + category + feeMonth,
      三化分类: category,
      条款金额: '100',
      '产品本月计收不含税额/标品含税收入': '20',
    };
    const response = await request.post(root, {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      data: { source },
    });
    expect(response.ok(), await response.text()).toBeTruthy();
  }
  await page.goto(
    '/revenue?' +
      new URLSearchParams({
        month: '2026-09',
        state: 'UNRECOGNIZED',
        province: ids.province,
        search: projectName,
        page: '1',
      }),
  );
  await chooseActor(page, ids.admin);
  const classification = page.getByRole('button', { name: '三化分类', exact: true });
  await expect(classification).toHaveText('全部');
  await chooseListOptions(page, '三化分类', ['智能化']);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL(
    (url) =>
      url.searchParams.get('category') === '智能化' &&
      url.searchParams.get('month') === '2026-09' &&
      url.searchParams.get('state') === 'UNRECOGNIZED' &&
      url.searchParams.get('province') === ids.province &&
      url.searchParams.get('search') === projectName &&
      !url.searchParams.has('page'),
  );
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1);
  const table = page.getByRole('table');
  await expect(table).toContainText(projectName + '智能化2026-09');
  await expect(table.getByRole('columnheader', { name: '费用账期' })).toBeVisible();
  await expect(table.getByRole('cell', { name: '2026-09', exact: true })).toBeVisible();
  await expect(table.getByRole('cell', { name: '2026-10', exact: true })).toHaveCount(0);
  await expect(
    page.getByRole('region', { name: '当前筛选汇总' }).getByText('明细数').locator('..'),
  ).toHaveText('明细数1');
  await page.getByRole('link', { name: '详情', exact: true }).click();
  await page.getByRole('link', { name: '返回计收列表' }).click();
  await expect(classification).toHaveText('智能化');
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1);
});

test('项目省市控件对齐并保留多选联动', async ({ page }) => {
  await page.goto('/projects');
  await chooseActor(page, ids.admin);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const province = page.getByRole('button', { name: '归属省分', exact: true });
  const city = page.getByRole('button', { name: '归属地市／区县', exact: true });
  const stage = page.getByRole('button', { name: '当前阶段' });
  await expect(city).toBeDisabled();
  const font = (element: Element) => {
    const css = getComputedStyle(element);
    return [css.fontFamily, css.fontSize, css.fontWeight, css.lineHeight, css.color];
  };
  const stageLabel = await stage.locator('../..').locator(':scope > span').evaluate(font);
  for (const label of ['归属省分', '归属地市／区县']) {
    const caption = page.locator('.multi-select-field > span').filter({ hasText: label });
    expect(await caption.evaluate(font)).toEqual(stageLabel);
  }
  const style = (control: Element) => {
    const css = getComputedStyle(control);
    return [
      css.height,
      css.borderTopColor,
      css.borderRadius,
      css.fontFamily,
      css.fontSize,
      css.fontWeight,
      css.backgroundImage,
    ];
  };
  expect(await province.evaluate(style)).toEqual(await stage.evaluate(style));
  await province.click();
  await expect(province).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('textbox', { name: '项目名称', exact: true }).click();
  await expect(province).toHaveAttribute('aria-expanded', 'false');
  await expect(province).toHaveText('全部');
  await province.click();
  await province.evaluate((element) => {
    element.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: window }));
  });
  // 浏览器失焦时，焦点去向可能是 Window。
  expect(errors).toEqual([]);
  await expect(province).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('checkbox', { name: '广东省', exact: true }).check();
  await province.press('Escape');
  expect(await city.evaluate(style)).toEqual(await stage.evaluate(style));
  await city.click();
  await page.getByRole('textbox', { name: '项目名称', exact: true }).click();
  await expect(city).toHaveAttribute('aria-expanded', 'false');
  await city.click();
  await page.getByRole('checkbox', { name: '广州市', exact: true }).check();
  await page.getByRole('checkbox', { name: '深圳市', exact: true }).check();
  await city.press('Escape');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL((url) => url.searchParams.getAll('cityIds').length === 2);
  await page.reload();
  await expect(city).toHaveText('广州市；深圳市');
  await province.click();
  await page.getByRole('checkbox', { name: '浙江省', exact: true }).check();
  await province.press('Escape');
  await expect(city).toBeDisabled();
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL(
    (url) =>
      url.searchParams.getAll('provinceIds').length === 2 &&
      url.searchParams.getAll('cityIds').length === 0,
  );
});
