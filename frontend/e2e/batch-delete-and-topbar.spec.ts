import { requiredRevenueFields } from './support/revenue';
import { randomUUID } from 'node:crypto';
import { expect, test, type APIRequestContext } from '@playwright/test';
import { chooseActor, createProject, ids, post } from './support/projects';

const api = 'http://127.0.0.1:18080/api/v1';
const admin = { 'X-NDI-Actor-Id': ids.admin };
const reporter = { 'X-NDI-Actor-Id': ids.reporter };
type Versioned = { id: string; version: number };

function registers(prefix: string) {
  const location = { provinceId: ids.province, cityId: ids.city };
  return [
    [
      'REVENUE',
      '/revenue/entries',
      {
        source: {
          ...requiredRevenueFields,
          月账期: '202611',
          自然客户名称: '验收客户',
          省份名称: '本地演示省',
          合同编号: prefix,
          条款编号: '1',
          三化分类: '数字化',
          条款金额: '100',
          '产品本月计收不含税额/标品含税收入': '10',
        },
      },
    ],
    [
      'PRODUCTION',
      '/production/plans',
      {
        ...location,
        reviewerName: '验收',
        planMonth: '2026-11',
        projectName: prefix,
        customerName: '验收客户',
        categories: ['GEWU'],
        gewuOrderAmount: '12',
      },
    ],
    [
      'GEWU',
      '/capability/gewu-deployments',
      {
        ...location,
        fields: {
          接口人: '验收',
          客户名称: '验收客户',
          项目名称: prefix,
          制造类型: '流程制造',
          国民经济行业分类: '食品制造业',
          所属行业: '食品',
          部署版本: '1',
          上一周期是否已接设备: '否',
        },
      },
    ],
    [
      'WANGWU',
      '/capability/wangwu',
      {
        provinceId: ids.province,
        fields: {
          接口人: prefix,
          部署地点: '本地',
          具体名称: prefix,
          部署进度: '已部署',
        },
      },
    ],
    [
      'AGENTS_LIVE',
      '/capability/agents-live',
      {
        provinceId: ids.province,
        fields: {
          接口人: prefix,
          省分智能化接口人: prefix,
          环节: '生产制造',
          智能体名称: prefix,
          应用项目名称: prefix,
          最终客户名称: '验收客户',
          智能体功能简述: '验收',
          是否为三方生态迁移: '否',
          是否计收: '未计收',
          是否在计收表中出现: '否',
        },
      },
    ],
    [
      'AGENTS_DEVELOPMENT',
      '/capability/agents-development',
      {
        provinceId: ids.province,
        fields: {
          接口人: prefix,
          智能体名称: prefix,
          应用项目名称: prefix,
        },
      },
    ],
  ] as const;
}

async function create(request: APIRequestContext, path: string, data: unknown): Promise<Versioned> {
  const response = await request.post(api + path, { headers: admin, data });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data;
}

test('管理员可批量删除六个台账，非管理员与过期版本被拒绝', async ({ request }, info) => {
  test.skip(info.project.name !== 'desktop');
  const first = registers(randomUUID());
  const second = registers(randomUUID());
  for (const [index, [register, path, data]] of first.entries()) {
    const a = await create(request, path, data);
    const b = await create(request, path, second[index][2]);
    const records = [a, b]
      .map(({ id, version }) => ({ id, version }))
      .sort((x, y) => x.id.localeCompare(y.id));
    const endpoint = api + '/admin/record-deletions/' + register;
    expect((await request.post(endpoint, { headers: reporter, data: { records } })).status()).toBe(
      403,
    );
    const stale = [records[0], { ...records[1], version: records[1].version + 1 }];
    const rejected = await request.post(endpoint, { headers: admin, data: { records: stale } });
    expect(rejected.status(), await rejected.text()).toBe(409);
    const accepted = await request.post(endpoint, { headers: admin, data: { records } });
    expect(accepted.ok(), await accepted.text()).toBeTruthy();
    expect((await accepted.json()).data).toBe(2);
    await create(request, path, data);
  }
});

test('按筛选跨页删除排产并保留未命中记录', async ({ page, request }, info) => {
  test.skip(info.project.name !== 'desktop');
  const prefix = '跨页删除-' + randomUUID();
  const [, path, input] = registers(prefix)[1];
  const untouched = await create(request, path, { ...input, projectName: randomUUID() });
  for (let i = 0; i < 101; i++) {
    await create(request, path, { ...input, projectName: prefix + '-' + i });
  }
  await page.goto('/scheduling');
  await chooseActor(page, ids.admin);
  const button = page.getByRole('button', { name: '删除筛选结果' });
  await expect(button).toBeDisabled();
  await page.goto('/scheduling?search=' + encodeURIComponent(prefix) + '&page=1');
  await button.click();
  const confirmation = page.getByRole('dialog', { name: '确认删除筛选结果' });
  await expect(confirmation).toContainText('共匹配 101 条记录');
  await confirmation.getByRole('button', { name: '确认删除', exact: true }).click();
  await expect(page.getByText('已删除 101 条记录')).toBeVisible();
  const remaining = await request.get(api + path + '?search=' + encodeURIComponent(prefix), {
    headers: admin,
  });
  expect((await remaining.json()).data.totalElements).toBe(0);
  expect((await request.get(api + path + '/' + untouched.id, { headers: admin })).ok()).toBe(true);
});

test('项目与计收保留待审批条目并删除其他筛选结果', async ({ page, request }) => {
  const prefix = '待审批保留-' + randomUUID();
  const pending = await createProject(request, prefix + '-待审');
  const removable = await createProject(request, prefix + '-可删除');
  await post(request, ids.reporter, `/${pending.id}/major-changes`, {
    changeType: 'PROJECT_NAME',
    initiatorLevel: 'CITY',
    approverId: ids.leader,
    reason: '验证筛选删除保留待审项目',
    projectName: prefix + '-调整',
  });
  await page.goto('/projects?projectName=' + encodeURIComponent(prefix));
  await chooseActor(page, ids.admin);
  const button = page.getByRole('button', { name: '删除筛选结果' });
  await button.click();
  const confirmation = page.getByRole('dialog', { name: '确认删除筛选结果' });
  await expect(confirmation).toContainText('待审批条目将保留');
  await confirmation.getByRole('button', { name: '确认删除', exact: true }).click();
  await expect(page.getByText('已删除 1 条记录，保留 1 条待审批记录')).toBeVisible();
  expect((await request.get(api + '/projects/' + removable.id, { headers: admin })).status()).toBe(
    404,
  );
  expect((await request.get(api + '/projects/' + pending.id, { headers: admin })).ok()).toBe(true);

  const [, path, input] = registers(prefix)[0];
  const pendingRevenue = await create(request, path, input);
  const removableRevenue = await create(request, path, {
    source: { ...input.source, 合同编号: prefix + '-可删除' },
  });
  const requestRecognition = await request.post(
    api + path + '/' + pendingRevenue.id + '/recognition',
    {
      headers: { 'X-NDI-Actor-Id': '55555555-5555-4555-8555-555555555555' },
      data: {
        version: pendingRevenue.version,
        approverId: '77777777-7777-4777-8777-777777777777',
        recognition: { amount: '25', reportedProjectName: prefix },
      },
    },
  );
  expect(requestRecognition.ok(), await requestRecognition.text()).toBe(true);
  await page.goto('/revenue?search=' + encodeURIComponent(prefix));
  await button.click();
  await expect(confirmation).toContainText('共匹配 2 条记录');
  await confirmation.getByRole('button', { name: '确认删除', exact: true }).click();
  await expect(page.getByText('已删除 1 条记录，保留 1 条待审批记录')).toBeVisible();
  expect(
    (await request.get(api + path + '/' + removableRevenue.id, { headers: admin })).status(),
  ).toBe(404);
  expect((await request.get(api + path + '/' + pendingRevenue.id, { headers: admin })).ok()).toBe(
    true,
  );
});

test('四个能力指标列表沿用各自筛选删除记录', async ({ page, request }) => {
  const prefix = randomUUID();
  const entries = registers(prefix).slice(2);
  const untouched = registers(randomUUID()).slice(2);
  await page.goto('/capability/gewu');
  await chooseActor(page, ids.admin);
  for (const [index, [kind, path, input]] of entries.entries()) {
    await create(request, path, input);
    const other = await create(request, path, untouched[index][2]);
    const url = kind === 'GEWU' ? '/capability/gewu?search=' : path + '?reviewer=';
    await page.goto(url + prefix);
    await page.getByRole('button', { name: '删除筛选结果' }).click();
    const confirmation = page.getByRole('dialog', { name: '确认删除筛选结果' });
    await expect(confirmation).toContainText('共匹配 1 条记录');
    await confirmation.getByRole('button', { name: '确认删除', exact: true }).click();
    await expect(page.getByText('已删除 1 条记录')).toBeVisible();
    expect((await request.get(api + path + '/' + other.id, { headers: admin })).ok()).toBe(true);
  }
});

test('管理员勾选并删除，项目标题与阶段切换置于固定栏', async ({ page, request }) => {
  const prefix = randomUUID();
  const [, path, input] = registers(prefix)[3];
  await create(request, path, input);
  await page.goto(`/capability/wangwu?reviewer=${prefix}`);
  await chooseActor(page, ids.admin);
  const header = page.getByRole('banner');
  await expect(header.getByText('工业三化运营管理平台')).toBeVisible();
  await expect(header.getByRole('heading', { name: '万悟部署情况' })).toBeVisible();
  await page.getByRole('checkbox', { name: '选择本页全部记录' }).check();
  await page.getByRole('button', { name: '批量删除（1）' }).click();
  await page
    .getByRole('dialog', { name: '确认批量删除' })
    .getByRole('button', { name: '确认删除', exact: true })
    .click();
  await expect(page.getByText('已删除 1 条记录')).toBeVisible();
  await expect(page.getByText('暂无符合条件的记录')).toBeVisible();
  let releaseProjectQuery = () => {};
  const projectQueryGate = new Promise<void>((resolve) => {
    releaseProjectQuery = resolve;
  });
  await page.route(
    (url) => url.pathname === '/api/v1/projects' && url.searchParams.get('stage') === 'BIDDING',
    async (route) => {
      await projectQueryGate;
      await route.continue();
    },
  );
  for (const [url, title] of [
    ['/projects', '项目列表'],
    ['/workbench', '项目管理驾驶舱'],
  ]) {
    await page.goto(url);
    await expect(header.getByRole('heading', { name: title })).toBeVisible();
    const stages = header.getByRole('group', { name: '项目阶段快捷筛选' });
    await stages.getByRole('button', { name: '招投标' }).click();
    await expect(page).toHaveURL(/stage=BIDDING/);
    await expect(stages.getByRole('button', { name: '招投标' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await page.evaluate(() => {
      const spacer = document.createElement('div');
      spacer.style.height = '200vh';
      document.querySelector('main')?.append(spacer);
      window.scrollTo(0, 1500);
    });
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(500);
    if (url === '/projects') {
      releaseProjectQuery();
      await expect(page.getByText('项目清单', { exact: true })).toBeVisible();
      await page.evaluate(
        () =>
          new Promise((resolve) => {
            requestAnimationFrame(() => requestAnimationFrame(() => resolve(null)));
          }),
      );
    }
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(500);
    await expect.poll(async () => (await header.boundingBox())?.y).toBe(0);
  }
});
