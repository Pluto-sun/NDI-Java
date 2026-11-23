import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, ids } from './support/projects';
import { pickMonth } from './support/monthPicker';

test('排产和计收筛选月可选择、清空并恢复', async ({ page }) => {
  for (const [path, label] of [
    ['/scheduling', '排产月份'],
    ['/revenue', '月账期'],
  ]) {
    await page.goto(path);
    if (path === '/scheduling') await chooseActor(page, ids.admin);
    const month = page.getByLabel(label, { exact: true });
    await pickMonth(month, '2026-10');
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page).toHaveURL(/month=2026-10/);
    await page.reload();
    await expect(month).toHaveText('2026-10');
    await pickMonth(month, '');
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page).not.toHaveURL(/month=/);
  }
});

test('计收分类和现有筛选组合，明细与汇总一致，详情返回恢复', async ({ page, request }) => {
  const root = 'http://127.0.0.1:18080/api/v1/revenue/entries';
  const projectName = '分类测试-' + randomUUID();
  for (const category of ['数字化', '智能化']) {
    const source = {
      月账期: '2026-10',
      项目名称: projectName + category,
      合同编号: projectName + category,
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
        month: '2026-10',
        state: 'UNRECOGNIZED',
        province: 'UNASSIGNED',
        search: projectName,
        page: '1',
      }),
  );
  await chooseActor(page, ids.admin);
  const group = page.getByRole('banner').getByRole('group', { name: '计收三化分类' });
  const tabs = group.getByRole('button');
  await expect(tabs).toHaveText(['全部', '网络化', '数字化', '智能化']);
  await expect(group.getByRole('button', { name: '全部', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.locator('#main-content').evaluate((content) => {
    content.style.minHeight = '180vh';
    window.scrollTo(0, document.body.scrollHeight);
  });
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(200);
  for (const tab of await tabs.all()) await expect(tab).toBeInViewport({ ratio: 1 });
  await group.getByRole('button', { name: '智能化', exact: true }).click();
  await expect(page).toHaveURL(
    (url) =>
      url.searchParams.get('category') === '智能化' &&
      url.searchParams.get('month') === '2026-10' &&
      url.searchParams.get('state') === 'UNRECOGNIZED' &&
      url.searchParams.get('province') === 'UNASSIGNED' &&
      url.searchParams.get('search') === projectName &&
      !url.searchParams.has('page'),
  );
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1);
  await expect(page.getByRole('table')).toContainText(projectName + '智能化');
  await expect(
    page.getByRole('region', { name: '当前筛选汇总' }).getByText('明细数').locator('..'),
  ).toHaveText('明细数1');
  await page.getByRole('link', { name: '详情', exact: true }).click();
  await page.getByRole('link', { name: '返回计收列表' }).click();
  await expect(group.getByRole('button', { name: '智能化', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1);
});

test('项目省市控件对齐并保留多选联动', async ({ page }) => {
  await page.goto('/projects');
  await chooseActor(page, ids.admin);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const province = page.locator('summary[aria-label="归属省分"]');
  const city = page.locator(
    'summary[aria-label="归属地市／区县"], button[aria-label="归属地市／区县"]',
  );
  const stage = page.getByRole('combobox', { name: '当前阶段' });
  await expect(city).toBeDisabled();
  const signing = page.getByRole('combobox', { name: '签约主体' });
  for (const control of [province, city]) {
    expect((await control.boundingBox())!.width).toBeGreaterThan(
      (await signing.boundingBox())!.width,
    );
  }
  const font = (element: Element) => {
    const css = getComputedStyle(element);
    return [css.fontFamily, css.fontSize, css.fontWeight, css.lineHeight, css.color];
  };
  const stageLabel = await stage.locator('..').evaluate(font);
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
  const provincePanel = province.locator('..');
  await province.click();
  await expect(provincePanel).toHaveAttribute('open', '');
  await page.getByText('查看权限范围内的项目主档，按编号、名称、行业、归属和阶段定位。').click();
  await expect(provincePanel).not.toHaveAttribute('open');
  await expect(province).toHaveText('全部');
  await province.click();
  await province.evaluate((element) => {
    element.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: window }));
  });
  // 浏览器失焦时，焦点去向可能是 Window。
  expect(errors).toEqual([]);
  await expect(provincePanel).not.toHaveAttribute('open');
  await province.click();
  await page.getByRole('checkbox', { name: '广东省', exact: true }).check();
  await province.press('Escape');
  expect(await city.evaluate(style)).toEqual(await stage.evaluate(style));
  await city.click();
  await page.getByRole('textbox', { name: '项目名称', exact: true }).click();
  await expect(city.locator('..')).not.toHaveAttribute('open');
  await city.click();
  await page.getByRole('checkbox', { name: '广州市', exact: true }).check();
  await page.getByRole('checkbox', { name: '深圳市', exact: true }).check();
  await city.press('Escape');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL((url) => url.searchParams.getAll('cityIds').length === 2);
  await page.reload();
  await expect(city).toHaveText('已选 2 项');
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
