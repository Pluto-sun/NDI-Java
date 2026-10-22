import { randomInt, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, get, post, ids } from './support/projects';

test('近期动态遵守地市和省分范围，待审驳回撤销不展示，生效后才追加动态', async ({
  page,
  request,
}) => {
  const suffix = randomUUID();
  const own = await createProject(request, `动态甲-${suffix}`);
  const account = await request.post('http://127.0.0.1:18080/api/v1/admin/users', {
    headers: { 'X-NDI-Actor-Id': ids.admin },
    data: {
      account: {
        loginName: `136${randomInt(10000000, 99999999)}`,
        displayName: '动态乙市用户',
        email: '',
        roleCodes: ['CITY_REPORTER'],
        provinceId: ids.province,
        cityId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      },
      password: 'Recent-E2e-2026!',
    },
  });
  expect(account.ok(), await account.text()).toBeTruthy();
  const otherActor: string = (await account.json()).data.id;
  const other = await post<{ id: string }>(request, otherActor, '', {
    provinceId: ids.province,
    cityId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    projectName: `动态乙-${suffix}`,
    customerName: '动态范围客户',
    industryCode: 'C25',
    customerNeed: '核对动态权限',
    totalAmount: '100',
    provinceLeaderUserId: ids.leader,
    cityAccountManagerName: '乙市经理',
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
    initialProgress: '开始交流',
    nextAction: '跟进',
    confirmNew: true,
    duplicateReason: '隔离验收',
  });
  const recent = () =>
    get<{ projectId: string; summary: string }[]>(
      request,
      ids.reporter,
      '/workbench/recent-activities',
    );
  const count = async () => (await recent()).filter((item) => item.projectId === own.id).length;
  const initial = await count();
  expect(initial).toBe(1);
  async function submit(name: string) {
    return post<{ id: string }>(request, ids.reporter, `/${own.id}/major-changes`, {
      changeType: 'PROJECT_NAME',
      initiatorLevel: 'CITY',
      approverId: ids.leader,
      reason: '核对动态生效时机',
      projectName: name,
    });
  }
  const rejected = await submit(`驳回-${suffix}`);
  expect(await count()).toBe(initial);
  await post(request, ids.leader, `/approvals/${rejected.id}/decision`, {
    approved: false,
    opinion: '需补充信息',
  });
  expect(await count()).toBe(initial);
  const withdrawn = await submit(`撤销-${suffix}`);
  await post(request, ids.reporter, `/approvals/${withdrawn.id}/withdraw`, {});
  expect(await count()).toBe(initial);
  const approvedName = `生效-${suffix}`;
  const approved = await submit(approvedName);
  await page.goto('/workbench');
  await chooseActor(page, ids.reporter);
  await page
    .getByRole('button', { name: '展开高级分析：历史月报、地图下钻与行业分析', exact: true })
    .click();
  const activities = page.getByRole('region', { name: '近期项目变动', exact: true });
  const ownRows = activities
    .getByRole('row')
    .filter({ has: page.locator(`a[href="/projects/${own.id}"]`) });
  await expect(activities.locator(`a[href="/projects/${own.id}"]`)).toHaveCount(initial);
  await expect(activities.locator(`a[href="/projects/${other.id}"]`)).toHaveCount(0);
  await expect(ownRows.getByText(/重大变更审批通过/)).toHaveCount(0);
  await post(request, ids.leader, `/approvals/${approved.id}/decision`, {
    approved: true,
    opinion: '',
  });
  await activities.getByRole('button', { name: '刷新动态', exact: true }).click();
  await expect(activities.locator(`a[href="/projects/${own.id}"]`)).toHaveCount(initial + 1);
  await expect(ownRows.getByText('重大变更审批通过', { exact: true })).toBeVisible();
  await expect(activities.getByRole('link', { name: approvedName, exact: true })).toHaveCount(2);
  expect(await count()).toBe(initial + 1);
  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, ids.leader);
  await page
    .getByRole('button', { name: '展开高级分析：历史月报、地图下钻与行业分析', exact: true })
    .click();
  await expect(activities.locator(`a[href="/projects/${own.id}"]`)).toHaveCount(2);
  await expect(activities.locator(`a[href="/projects/${other.id}"]`)).toHaveCount(1);
  await activities.locator(`a[href="/projects/${other.id}"]`).click();
  await expect(page.getByRole('heading', { name: `动态乙-${suffix}`, exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});
