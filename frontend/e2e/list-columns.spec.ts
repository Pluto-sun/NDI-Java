import { expect, test, type Locator } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { chooseActor, ids } from './support/projects';
import { requiredRevenueFields } from './support/revenue';

const api = 'http://127.0.0.1:18080/api/v1';
const admin = { 'X-NDI-Actor-Id': ids.admin };
const reader = { 'X-NDI-Actor-Id': ids.reporter };
const paths = {
  REVENUE: '/revenue',
  PROJECTS: '/projects',
  PRODUCTION: '/scheduling',
  GEWU: '/capability/gewu',
  WANGWU: '/capability/wangwu',
  AGENTS_LIVE: '/capability/agents-live',
  AGENTS_DEVELOPMENT: '/capability/agents-development',
};

function columns(table: Locator) {
  return table
    .locator('th[data-column-id]')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-column-id')));
}

test('计收列个性化与管理员固定列跨账号生效，横向滚动保持详情入口', async ({
  page,
  context,
  request,
}, info) => {
  test.setTimeout(90_000);
  const project = '列设置验收-' + randomUUID();
  const created = await request.post(api + '/revenue/entries', {
    headers: admin,
    data: {
      source: {
        ...requiredRevenueFields,
        月账期: '202610',
        项目名称: project,
        合同编号: project,
        三化分类: '数字化',
        条款金额: '800.00',
        '产品本月计收不含税额/标品含税收入': '222.00',
      },
    },
  });
  expect(created.ok(), await created.text()).toBe(true);
  const route = '/revenue?search=' + encodeURIComponent(project);
  await page.goto(route);
  await chooseActor(page, ids.reporter);
  const table = page.getByRole('table', { name: '计收明细清单' });
  const settings = page.getByRole('button', { name: '列设置', exact: true });
  const panel = page.getByRole('dialog', { name: '列设置', exact: true });
  const check = (name: string) => panel.getByRole('checkbox', { name, exact: true });
  await settings.click();
  await check('来源项目').uncheck();
  await panel.getByRole('button', { name: '省份上移', exact: true }).click();
  await panel.getByRole('button', { name: '应用', exact: true }).click();
  await expect
    .poll(() => columns(table))
    .toEqual([
      'province',
      'fee-month',
      'city',
      'category',
      'customer',
      'contract',
      'clause',
      'clause-amount',
      'source-amount',
      'recognized-amount',
      'reported-project',
      'integrated-province',
      'state',
    ]);
  await settings.click();
  await panel.getByRole('button', { name: '恢复默认', exact: true }).click();
  await panel.getByRole('button', { name: '取消', exact: true }).click();
  await expect(table.locator('th[data-column-id="source-project"]')).toHaveCount(0);

  const management = await context.newPage();
  try {
    await management.goto('/admin/list-columns');
    await chooseActor(management, ids.admin);
    const options = management.getByRole('region', { name: '可固定的业务列' });
    const fixedOrder = management.getByRole('region', { name: '固定列顺序' });
    const save = management.getByRole('button', { name: '保存', exact: true });
    await options.getByRole('checkbox', { name: '费用账期', exact: true }).click();
    await expect(fixedOrder.getByRole('checkbox', { name: '费用账期', exact: true })).toBeChecked();
    await options.getByRole('checkbox', { name: '来源项目', exact: true }).click();
    await expect(fixedOrder.getByRole('checkbox', { name: '来源项目', exact: true })).toBeChecked();
    await save.click();
    await expect(management.getByText('固定列配置已保存。')).toBeVisible();
    await management.screenshot({ path: info.outputPath('list-columns-admin.png') });
    await management.goto(route);
    await expect(management.getByRole('checkbox', { name: '选择本页全部记录' })).toBeVisible();
    const adminColumns = management.getByRole('table', { name: '计收明细清单' });
    await expect(adminColumns.locator('thead th').first()).toHaveClass(/batch-select-cell/);
    await expect(adminColumns.locator('thead th').nth(1)).toHaveAttribute(
      'data-column-id',
      'fee-month',
    );
    await page.reload();
    await settings.click();
    await expect(check('费用账期')).toBeDisabled();
    await expect(check('来源项目')).toBeChecked();
    await expect(check('来源项目')).toBeDisabled();
    await expect(panel.getByRole('button', { name: '来源项目上移', exact: true })).toHaveCount(0);
    await page.screenshot({ path: info.outputPath('list-columns-personal.png') });
    await panel.getByRole('button', { name: '取消', exact: true }).click();
    await expect(table.locator('tbody td[data-column-id="source-project"]')).toHaveText(project);

    const fee = table.locator('th[data-column-id="fee-month"]');
    const projectHeader = table.locator('th[data-column-id="source-project"]');
    const feeHandle = fee.getByRole('button', { name: '调整费用账期列宽' });
    const before = (await fee.boundingBox())!;
    const handle = (await feeHandle.boundingBox())!;
    await page.mouse.move(handle.x + 4, handle.y + 12);
    await page.mouse.down();
    await page.mouse.move(handle.x + 52, handle.y + 12, { steps: 5 });
    await expect(page.locator('.column-resize-guide')).toBeVisible();
    expect((await fee.boundingBox())!.width).toBeCloseTo(before.width, 0);
    await page.mouse.up();
    await expect(page.locator('.column-resize-guide')).toHaveCount(0);
    await expect
      .poll(async () => (await fee.boundingBox())!.width)
      .toBeGreaterThan(before.width + 35);
    const resized = (await fee.boundingBox())!.width;
    const scroll = page.getByRole('region', { name: '计收明细横向滚动区域' });
    const scrollBox = (await scroll.boundingBox())!;
    await scroll.evaluate((element) => {
      element.scrollLeft = element.scrollWidth;
    });
    await expect.poll(async () => (await fee.boundingBox())!.x).toBeCloseTo(scrollBox.x, 0);
    await expect
      .poll(async () => (await projectHeader.boundingBox())!.x)
      .toBeCloseTo(scrollBox.x + resized, 0);
    const displayedProject = table.locator('td[data-column-id="source-project"]');
    expect((await displayedProject.boundingBox())!.x).toBeCloseTo(
      (await projectHeader.boundingBox())!.x,
      0,
    );
    await page.screenshot({ path: info.outputPath('list-columns-scrolled.png') });
    await table.getByRole('link', { name: '详情', exact: true }).click();
    await expect(page).toHaveURL(/\/revenue\/[a-f0-9-]+/);
    await page.goBack();
    await expect(fee).toBeVisible();
    expect((await fee.boundingBox())!.width).toBeCloseTo(resized, 0);

    await management.goto('/admin/list-columns');
    const listItems = fixedOrder.getByRole('listitem');
    await listItems.first().locator('.column-drag').dragTo(listItems.nth(1));
    await expect(listItems.first()).toContainText('来源项目');
    await save.click();
    await expect(management.getByText('固定列配置已保存。')).toBeVisible();
    await page.reload();
    await expect
      .poll(async () => (await columns(table)).slice(0, 2))
      .toEqual(['source-project', 'fee-month']);

    await management.getByRole('button', { name: '清空固定列', exact: true }).click();
    await save.click();
    await expect(management.getByText('固定列配置已保存。')).toBeVisible();
    await page.reload();
    await expect(settings).toBeEnabled();
    await expect(table.locator('th[data-column-id="source-project"]')).toHaveCount(0);
    expect((await fee.boundingBox())!.width).toBeCloseTo(resized, 0);
    await settings.click();
    await panel.getByRole('button', { name: '恢复默认', exact: true }).click();
    await panel.getByRole('button', { name: '应用', exact: true }).click();
    await expect(table.locator('th[data-column-id="source-project"]')).toBeVisible();
    expect((await fee.boundingBox())!.width).toBeCloseTo(before.width, 0);
    const forbidden = await request.put(api + '/admin/list-columns/REVENUE', {
      headers: reader,
      data: { fixedFields: ['fee-month'] },
    });
    expect(forbidden.status()).toBe(403);
  } finally {
    await request.put(api + '/admin/list-columns/REVENUE', {
      headers: admin,
      data: { fixedFields: [] },
    });
  }
});

test('七张表使用独立的列偏好，已有偏好容忍失效字段并显示新增列', async ({ page, request }) => {
  test.setTimeout(90_000);
  const response = await request.get(api + '/admin/list-columns', { headers: admin });
  expect(response.ok(), await response.text()).toBe(true);
  const { lists } = (await response.json()).data as {
    lists: { id: keyof typeof paths; fields: { id: string; label: string }[] }[];
  };
  await page.goto('/projects');
  await chooseActor(page, ids.admin);
  for (const { id, fields } of lists) {
    await page.goto(paths[id]);
    const table = page.locator('table.personalized-table');
    const settings = page.getByRole('button', { name: '列设置', exact: true });
    await expect(settings).toBeEnabled();
    await expect.poll(() => columns(table)).toEqual(fields.map((field) => field.id));
  }
  await page.evaluate(
    ({ account }) => {
      localStorage.setItem(
        'ndi.list-columns:' + account + ':REVENUE',
        JSON.stringify({
          order: ['old-column', 'contract', 'contract'],
          hidden: ['old-column', 'source-project'],
          widths: { 'old-column': 400, contract: 240, 'fee-month': 'bad' },
        }),
      );
    },
    { account: ids.admin },
  );
  await page.goto('/revenue');
  await expect(page.getByRole('button', { name: '列设置', exact: true })).toBeEnabled();
  const table = page.locator('table.personalized-table');
  await expect(table.locator('th[data-column-id]').first()).toHaveAttribute(
    'data-column-id',
    'contract',
  );
  await expect(table.locator('th[data-column-id="source-project"]')).toHaveCount(0);
  await expect(table.locator('th[data-column-id="fee-month"]')).toBeVisible();
  await page.goto('/scheduling');
  await expect(page.getByRole('button', { name: '列设置', exact: true })).toBeEnabled();
  await expect(table.locator('th[data-column-id]').first()).toHaveAttribute(
    'data-column-id',
    'reviewer',
  );
});

test('较窄 PC 窗口有四列固定时保留滚动区域及两侧功能列', async ({ page, request }, info) => {
  await page.setViewportSize({ width: 1100, height: 820 });
  const fields = ['source-project', 'reported-project', 'source-amount', 'recognized-amount'];
  const url = api + '/admin/list-columns/REVENUE';
  const saved = await request.put(url, { headers: admin, data: { fixedFields: fields } });
  expect(saved.ok(), await saved.text()).toBe(true);
  try {
    await page.addInitScript(
      ({ key, fields }) => {
        localStorage.setItem(
          key,
          JSON.stringify({ widths: Object.fromEntries(fields.map((id) => [id, 520])) }),
        );
      },
      { key: 'ndi.list-columns:' + ids.admin + ':REVENUE', fields },
    );
    await page.goto('/revenue');
    await chooseActor(page, ids.admin);
    const scroll = page.getByRole('region', { name: '计收明细横向滚动区域' });
    const table = scroll.getByRole('table');
    await expect(page.getByRole('button', { name: '列设置', exact: true })).toBeEnabled();
    await scroll.evaluate((element) => {
      element.scrollLeft = element.scrollWidth;
    });
    const frame = (await scroll.boundingBox())!;
    const selection = (await table.locator('th.batch-select-cell').boundingBox())!;
    const actions = (await table.locator('th.sticky-list-action').boundingBox())!;
    const fixed = await table.locator('th.sticky-business-column').all();
    expect(fixed).toHaveLength(4);
    let edge = selection.x + selection.width;
    for (const cell of fixed) {
      const box = (await cell.boundingBox())!;
      expect(box.x).toBeCloseTo(edge, 0);
      edge += box.width;
    }
    expect(selection.x).toBeCloseTo(frame.x, 0);
    expect(actions.x + actions.width).toBeCloseTo(frame.x + frame.width, 0);
    expect(edge - selection.x - selection.width).toBeLessThanOrEqual(
      (frame.width - selection.width - actions.width) * 0.55,
    );
    expect(actions.x - edge).toBeGreaterThan(100);
    await page.screenshot({ path: info.outputPath('list-columns-1100px.png') });
  } finally {
    await request.put(url, { headers: admin, data: { fixedFields: [] } });
  }
});
