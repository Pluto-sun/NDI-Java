import { chooseListOptions } from './support/listFilters';
import { randomUUID } from 'node:crypto';
import { expect, test, type APIRequestContext, type Locator } from '@playwright/test';
import { chooseActor, ids, post } from './support/projects';
import { requiredRevenueFields } from './support/revenue';

const api = 'http://127.0.0.1:18080/api/v1';
const as = (actor: string) => ({ 'X-NDI-Actor-Id': actor });
async function add(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.post(api + path, { headers: as(ids.admin), data });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as { id: string };
}

test('组织归属管理允许设置和取消，筛选和详情保持来源原值', async ({ page, request }, info) => {
  const contract = randomUUID();
  const organization = `联通（湖北）产业互联网有限公司-${contract.slice(0, 8)}`;
  const entry = await add(request, '/revenue/entries', {
    source: {
      ...requiredRevenueFields,
      月账期: '202610',
      省份名称: '具体组织',
      '地市/组织名称': organization,
      合同编号: contract,
      三化分类: '数字化',
      条款金额: '30',
      '产品本月计收不含税额/标品含税收入': '0',
    },
  });
  const endpoint = '/revenue/organizations';
  expect((await request.get(api + endpoint, { headers: as(ids.reporter) })).status()).toBe(403);
  expect(
    (await request.get(api + '/revenue/entries/' + entry.id, { headers: as(ids.leader) })).status(),
  ).toBe(403);
  await page.goto('/revenue/organizations');
  await chooseActor(page, ids.admin);
  const row = page.getByRole('row', { name: new RegExp(organization.replace(/[（）]/g, '.')) });
  const provinceColumn = page.getByRole('columnheader', { name: '归属省份', exact: true });
  expect(
    await provinceColumn.evaluate((element) => {
      const text = document.createRange();
      text.selectNodeContents(element);
      return text.getClientRects().length;
    }),
  ).toBe(1);

  await row.getByRole('button', { name: '配置' }).click();
  await row.getByRole('combobox').selectOption(ids.province);
  await row.getByRole('button', { name: '保存' }).click();
  await expect(row.getByRole('button', { name: '配置' })).toBeVisible();
  await expect(row).toContainText('本地演示省');
  await page.screenshot({ path: info.outputPath('organization-assignment.png'), fullPage: true });
  const filter = new URLSearchParams({ province: ids.province, search: contract });
  const result = await request.get(api + '/revenue/entries?' + filter, { headers: as(ids.leader) });
  expect((await result.json()).data).toMatchObject({ totalElements: 1, summary: { total: 1 } });
  const allowed = await request.get(api + '/revenue/entries/' + entry.id, {
    headers: as(ids.leader),
  });
  expect((await allowed.json()).data.source).toMatchObject({
    省份名称: '具体组织',
    '地市/组织名称': organization,
  });
  await row.getByRole('button', { name: '配置' }).click();
  await row.getByRole('combobox').selectOption('');
  await row.getByRole('button', { name: '保存' }).click();
  await expect(row.getByRole('button', { name: '配置' })).toBeVisible();
  await expect(row).toContainText('未分配');
  expect(
    (await request.get(api + '/revenue/entries/' + entry.id, { headers: as(ids.leader) })).status(),
  ).toBe(403);
});

test('产品筛选按 OR 去重，保存到列表 URL 并交给导出', async ({ page, request }, info) => {
  const tag = randomUUID();
  for (const [suffix, products] of [
    ['-双产品', ['NETWORK', 'GEWU']],
    ['-万悟', ['WANGWU']],
  ] as const) {
    await post(request, ids.reporter, '', {
      provinceId: ids.province,
      cityId: ids.city,
      projectName: tag + suffix,
      customerNeed: '验收产品选项',
      initialProgress: '已整理产品需求',
      nextAction: '核对产品范围',
      totalAmount: '100',
      responsiblePersonNames: ['验收'],
      categoryAmounts: products.map((categoryCode) => ({ categoryCode, expectedAmount: '10' })),
      confirmNew: true,
      duplicateReason: '产品验收记录归属和用途独立',
    });
  }
  await page.goto('/projects?projectName=' + tag);
  await chooseActor(page, ids.admin);
  await chooseListOptions(page, '当前阶段', ['待计收']);
  await chooseListOptions(page, '当前阶段', [], true);
  const products = page.getByRole('button', { name: '产品类别', exact: true });
  await chooseListOptions(page, '产品类别', ['5G工业专网', '格物工业互联网平台']);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page).toHaveURL((url) => url.searchParams.getAll('productCodes').length === 2);
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1);
  await page.getByRole('link', { name: /^详情：/ }).click();
  await page.goBack();
  await expect(products).toHaveText('5G工业专网；格物工业互联网平台');
  await page.reload();
  await expect(products).toHaveText('5G工业专网；格物工业互联网平台');
  await page.screenshot({ path: info.outputPath('product-filters.png'), fullPage: false });
  const exporting = page.waitForResponse(
    (response) =>
      response.url().endsWith('/projects/exports') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '按当前条件导出 CSV' }).click();
  await page.getByRole('button', { name: '生成导出文件' }).click();
  expect((await exporting).request().postDataJSON().productCodes).toEqual(['NETWORK', 'GEWU']);
});

async function gap(trigger: Locator, panel: Locator) {
  const a = (await trigger.boundingBox())!;
  const b = (await panel.boundingBox())!;
  return Math.min(Math.abs(b.y - a.y - a.height), Math.abs(a.y - b.y - b.height));
}

test('下拉随页面及嵌套滚动对齐，选项可独立滚动，锚点离开后关闭', async ({ page }, info) => {
  await page.goto('/revenue');
  await chooseActor(page, ids.admin);
  const region = page.getByRole('form', { name: '计收筛选' });
  const trigger = region.getByRole('button', { name: '省份／具体组织' });
  const panel = page.getByRole('group', { name: '省份／具体组织', exact: true });
  const choices = panel.getByRole('dialog', { name: '省份／具体组织选项', exact: true });
  await expect(trigger).toBeVisible();
  await page.locator('#main-content').evaluate((element) => {
    element.style.minHeight = '200vh';
  });
  await trigger.click();
  await expect(panel).toBeVisible();
  await expect.poll(() => gap(trigger, panel)).toBeLessThanOrEqual(6);
  await expect(choices.getByRole('checkbox', { name: '广东省', exact: true })).toHaveCount(1);
  await choices.evaluate((element) => {
    element.scrollTop = 120;
  });
  await expect.poll(() => choices.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
  await page.evaluate(() => window.scrollBy(0, 30));
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(0);
  await expect.poll(() => gap(trigger, panel)).toBeLessThanOrEqual(6);
  await trigger.press('Escape');
  await expect(panel).toHaveCount(0);

  await page.evaluate(() => window.scrollTo(0, 0));
  await region.evaluate((element) => {
    Object.assign(element.style, { height: '180px', overflow: 'auto' });
    const spacer = document.createElement('div');
    spacer.setAttribute('aria-hidden', 'true');
    Object.assign(spacer.style, { height: '600px', gridColumn: '1 / -1' });
    element.append(spacer);
  });
  await trigger.click();
  await expect(panel).toBeVisible();
  await region.evaluate((element) => {
    element.scrollTop += 25;
  });
  await expect.poll(() => gap(trigger, panel)).toBeLessThanOrEqual(6);
  await page.screenshot({ path: info.outputPath('anchored-select.png'), fullPage: false });
  await region.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
  });
  await expect
    .poll(async () => (await region.boundingBox())!.y - (await trigger.boundingBox())!.y)
    .toBeGreaterThan((await trigger.boundingBox())!.height);
  await expect(panel).toHaveCount(0);
});

test('排产详情可直达，金额边线和响应式分组在查看与新增一致', async ({ page, request }, info) => {
  const name = '金额布局-' + randomUUID();
  const entry = await add(request, '/production/plans', {
    reviewerName: '验收',
    planMonth: '2026-11',
    provinceId: ids.province,
    cityId: ids.city,
    projectName: name,
    gewuOrderAmount: '20',
    gewuRevenueAmount: '8',
  });
  await page.goto('/scheduling/' + entry.id);
  await chooseActor(page, ids.admin);
  const product = page.getByRole('region', { name: '产品金额', exact: true });
  const block = product
    .locator('.production-product-amount')
    .filter({ hasText: '格物工业互联网平台' });
  await expect(block).toContainText('28');
  expect(await product.evaluate((element) => getComputedStyle(element).borderTopWidth)).toBe('1px');
  expect(await block.evaluate((element) => getComputedStyle(element).borderLeftWidth)).toBe('3px');
  expect(
    await block
      .locator('..')
      .evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(' ').length),
  ).toBe(info.project.name === 'small-screen' ? 1 : 2);
  await page.screenshot({ path: info.outputPath('production-detail.png'), fullPage: true });
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  await expect(product.getByLabel('格物工业互联网平台总排产金额')).toHaveText('28');
  await page.screenshot({ path: info.outputPath('production-edit.png'), fullPage: true });
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(block).toContainText('28');
  await page.goto('/scheduling/new');
  await expect(product.locator('.production-product-amount')).toHaveCount(4);
  await page.screenshot({ path: info.outputPath('production-create.png'), fullPage: true });
});

test('弹窗内的下拉跟随滚动并拦截 Escape，关闭下拉后仍可编辑', async ({ page }) => {
  await page.goto('/capability/gewu');
  await chooseActor(page, ids.admin);
  await page.getByRole('button', { name: '新增部署', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '新增格物部署' });
  await dialog.evaluate((element) => {
    element.style.maxHeight = '350px';
  });
  const trigger = dialog.getByRole('combobox', { name: '省份', exact: true });
  await trigger.press('ArrowDown');
  const panel = dialog.getByRole('listbox', { name: '可选项' });
  await expect(panel).toBeVisible();
  await dialog.evaluate((element) => {
    element.scrollTop = 25;
  });
  await expect.poll(() => gap(trigger, panel)).toBeLessThanOrEqual(6);
  await trigger.press('Escape');
  await expect(panel).toHaveCount(0);
  await expect(dialog).toBeVisible();
  await trigger.click();
  await expect(panel).toBeVisible();
  await dialog.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
  });
  await expect(panel).toHaveCount(0);
  await expect(dialog).toBeVisible();
});

test('五类详情与编辑 API 拦截超出范围的地市账号', async ({ request }) => {
  type Regions = { provinces: { id: string }[]; cities: { id: string; provinceId: string }[] };
  const response = await request.get(api + '/production/plans/reference-data', {
    headers: as(ids.admin),
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const regions = (await response.json()).data as Regions;
  const city = regions.cities.find((item) => item.provinceId !== ids.province)!;
  const location = { provinceId: city.provinceId, cityId: city.id };
  const name = '权限验收-' + randomUUID();
  const agent = {
    接口人: '验收',
    省分智能化接口人: '验收',
    环节: '生产制造',
    智能体名称: name,
    应用项目名称: name,
    最终客户名称: '验收客户',
    智能体功能简述: '验收功能',
    是否为三方生态迁移: '否',
    是否计收: '未计收',
    是否在计收表中出现: '否',
  };
  const production = {
    ...location,
    reviewerName: '验收',
    projectName: name,
    planMonth: '2026-11',
    networkOrderAmount: '5',
  };
  const gewu = {
    ...location,
    fields: {
      接口人: '验收',
      客户名称: '验收客户',
      项目名称: name,
      制造类型: '流程制造',
      国民经济行业分类: '食品制造业',
      所属行业: '食品',
      部署版本: '1',
      上一周期是否已接设备: '否',
    },
  };
  const datasets = [
    ['/production/plans', production, 'plan'],
    ['/capability/gewu-deployments', gewu, 'deployment'],
    [
      '/capability/wangwu',
      {
        provinceId: location.provinceId,
        fields: {
          接口人: '验收',
          部署地点: '企业',
          具体名称: name,
          部署进度: '未部署',
        },
      },
      'entry',
    ],
    ['/capability/agents-live', { provinceId: location.provinceId, fields: agent }, 'entry'],
    [
      '/capability/agents-development',
      { provinceId: location.provinceId, fields: { 智能体名称: name, 应用项目名称: name } },
      'entry',
    ],
  ] as const;
  for (const [path, input, key] of datasets) {
    const entry = await add(request, path, input);
    const detail = api + path + '/' + entry.id;
    expect((await request.get(detail, { headers: as(ids.admin) })).ok()).toBeTruthy();
    expect([403, 404]).toContain(
      (await request.get(detail, { headers: as(ids.reporter) })).status(),
    );
    expect([403, 404]).toContain(
      (
        await request.put(detail, {
          headers: as(ids.reporter),
          data: { version: 0, [key]: input },
        })
      ).status(),
    );
  }
});

test('详情定时刷新后，编辑仍使用开始编辑时的版本', async ({ page, request }) => {
  const plan = {
    reviewerName: '验收',
    planMonth: '2026-11',
    provinceId: ids.province,
    cityId: ids.city,
    projectName: '并发编辑-' + randomUUID(),
    gewuOrderAmount: '20',
  };
  const { id } = await add(request, '/production/plans', plan);
  const endpoint = api + '/production/plans/' + id;
  await page.goto('/scheduling/' + id + '?mode=edit');
  await chooseActor(page, ids.admin);
  const remark = page.getByLabel('备注（选填）', { exact: true });
  await remark.fill('详情里的修改');
  const changed = await request.put(endpoint, {
    headers: as(ids.admin),
    data: { version: 0, plan: { ...plan, remark: '其他账号先保存' } },
  });
  expect(changed.ok(), await changed.text()).toBeTruthy();
  const refreshed = page.waitForResponse(
    (result) =>
      result.url().endsWith('/production/plans/' + id) && result.request().method() === 'GET',
  );
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  expect((await (await refreshed).json()).data.version).toBe(1);
  await page.getByRole('button', { name: '保存排产', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('记录或任务已变化');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(page.getByText('其他账号先保存', { exact: true })).toBeVisible();
});
