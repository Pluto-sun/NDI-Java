import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { chooseActor, ids } from './support/projects';
import { chooseListOptions, submitRecognition } from './support/listFilters';
import { requiredRevenueFields } from './support/revenue';

const api = 'http://127.0.0.1:18080/api/v1';
const reviewer = '55555555-5555-4555-8555-555555555555';
const group = '77777777-7777-4777-8777-777777777777';

test('筛选搜索、键盘、独立清空与多值 URL 恢复', async ({ page }, info) => {
  await page.goto('/projects');
  await chooseActor(page, ids.admin);
  await page.setViewportSize({ width: 1140, height: 800 });
  const trigger = page.getByRole('button', { name: '归属省分', exact: true });
  await trigger.click();
  const search = page.getByRole('combobox', { name: '搜索归属省分', exact: true });
  await search.fill('粤粤');
  await expect(page.getByText('无匹配选项')).toBeVisible();
  await search.dispatchEvent('compositionstart', { data: '广' });
  await search.fill('广东');
  await search.dispatchEvent('compositionend', { data: '广东' });
  await search.press('ArrowDown');
  await page.keyboard.press('Space');
  await expect(page.getByRole('checkbox', { name: '广东省', exact: true })).toBeChecked();
  await expect(page.locator('.multi-select-option.selected')).toContainText('✓');
  await page.getByRole('button', { name: '清空搜索' }).click();
  await search.fill('江西');
  await search.press('Enter');
  await expect(trigger).toHaveText('广东省；江西省');
  await page.getByRole('button', { name: '清空搜索' }).click();
  await expect(page.getByRole('checkbox', { name: '广东省', exact: true })).toBeChecked();
  await page.screenshot({ path: info.outputPath('multiselect-desktop.png') });
  await search.press('Escape');
  await expect(trigger).toBeFocused();
  await expect(page.getByRole('button', { name: '归属地市／区县' })).toBeDisabled();
  await chooseListOptions(page, '当前阶段', ['方案交流', '待计收']);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL(
    (url) => url.searchParams.getAll('stage').join() === 'SOLUTION_EXCHANGE,COLLECTION',
  );
  await page.reload();
  await expect(trigger).toHaveText('广东省；江西省');
  await expect(page.getByRole('button', { name: '当前阶段' })).toHaveText('方案交流；待计收');
  for (const [path, label, value, param, expected] of [
    ['/scheduling', '排产月份', '2026-10', 'month', '2026-10'],
    ['/capability/gewu', '上一周期设备接入', '是', 'connected', '是'],
    ['/capability/wangwu', '部署进度', '已部署', 'progress', '已部署'],
    ['/capability/agents-live', '省分', '广东省', 'provinceId', ''],
    ['/capability/agents-development', '省分', '江西省', 'provinceId', ''],
  ]) {
    await page.goto(path);
    await chooseListOptions(page, label, [value]);
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page).toHaveURL((url) =>
      expected ? url.searchParams.get(param) === expected : url.searchParams.has(param),
    );
    await page.reload();
    await expect(page.getByRole('button', { name: label, exact: true })).toHaveText(value);
    await expect(page.getByRole('alert')).toHaveCount(0);
  }
});

test('长表的表头、固定操作和横轴同步，行内提交计收认定', async ({ page, request }, info) => {
  const prefix = '固定表格验收-' + randomUUID();
  const entries = await Promise.all(
    Array.from({ length: 24 }, async (_, i) => {
      const response = await request.post(api + '/revenue/entries', {
        headers: { 'X-NDI-Actor-Id': ids.admin },
        data: {
          source: {
            ...requiredRevenueFields,
            月账期: '202610',
            项目名称: prefix + i,
            合同编号: prefix + i,
            三化分类: i % 2 ? '数字化' : '智能化',
            条款编号: '1',
            条款金额: '100',
            '产品本月计收不含税额/标品含税收入': '30',
          },
        },
      });
      expect(response.ok(), await response.text()).toBe(true);
      return (await response.json()).data.id as string;
    }),
  );
  await page.goto('/revenue?search=' + encodeURIComponent(prefix));
  await chooseActor(page, reviewer);
  await chooseListOptions(page, '三化分类', ['数字化', '智能化']);
  await chooseListOptions(page, '费用账期', ['2026-09', '2026-10']);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL(
    (url) =>
      url.searchParams.getAll('month').length === 2 &&
      url.searchParams.getAll('category').length === 2,
  );
  const table = page.getByRole('table', { name: '计收明细清单' });
  const scroller = page.getByRole('region', { name: '计收明细横向滚动区域' });
  const slider = page.getByRole('slider', { name: '横向滚动表格' });
  for (const width of [1440, 1120]) {
    await page.setViewportSize({ width, height: 880 });
    await expect(table.locator('tbody tr')).toHaveCount(20);
    await scroller.scrollIntoViewIfNeeded();
    await slider.focus();
    await slider.press('End');
    await expect.poll(() => scroller.evaluate((el) => el.scrollLeft)).toBeGreaterThan(0);
    await scroller.evaluate((el) => {
      el.scrollTop = 230;
    });
    const bounds = (await scroller.boundingBox())!;
    const head = (await table
      .getByRole('columnheader', { name: '操作', exact: true })
      .boundingBox())!;
    expect(Math.abs(head.y - bounds.y)).toBeLessThan(3);
    expect(Math.abs(head.x + head.width - bounds.x - bounds.width)).toBeLessThan(24);
    await expect(slider).toBeInViewport();
    await page.screenshot({ path: info.outputPath('ledger-scroll-' + width + '.png') });
  }
  await scroller.evaluate((el) => {
    el.scrollTop = 0;
  });
  const first = table.locator('tbody tr').first();
  const id = new URL(
    (await first.getByRole('link', { name: '详情' }).getAttribute('href'))!,
    'http://localhost',
  ).pathname
    .split('/')
    .at(-1);
  expect(entries).toContain(id);
  await first.getByRole('button', { name: '认定', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '首次认定' });
  await dialog.getByRole('combobox', { name: '集团审核人' }).selectOption(group);
  await dialog.getByLabel('认定金额（元）').fill('25');
  await dialog.getByLabel('通报项目名称').fill(prefix);
  await dialog.getByRole('combobox', { name: '对应省分（集成）' }).selectOption(ids.province);
  const location = page.url();
  await submitRecognition(dialog);
  await expect(page.getByText('认定申请已提交')).toBeVisible();
  expect(page.url()).toBe(location);
  await expect(first.getByRole('button', { name: '认定', exact: true })).toHaveCount(0);
  await first.getByRole('link', { name: '详情' }).click();
  const action = page.getByRole('link', { name: '返回计收列表' });
  const before = (await action.boundingBox())!.y;
  await page.locator('.ledger-detail-body').evaluate((el) => {
    el.scrollTop = el.scrollHeight;
  });
  expect((await action.boundingBox())!.y).toBeCloseTo(before, 0);
  await action.click();
  await expect(page).toHaveURL(location);
});
