import { randomUUID, randomInt } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, ids, post } from './support/projects';

test('停用负责人保留项目姓名和责任信息，维护时仍须改选启用账号', async ({ page, request }) => {
  const name = `责任人-${randomUUID()}`;
  const created = await request.post('http://127.0.0.1:18080/api/v1/admin/users', {
    headers: { 'X-NDI-Actor-Id': ids.admin },
    data: {
      account: {
        loginName: `1${randomInt(3000000000, 10000000000)}`,
        displayName: name,
        email: null,
        roleCodes: ['PROVINCE_REPORTER'],
        provinceId: ids.province,
        cityId: null,
      },
      password: 'E2eTest@2026',
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  const leader = (await created.json()).data as { id: string; version: number };
  const project = await post<{ id: string }>(request, ids.reporter, '', {
    provinceId: ids.province,
    cityId: ids.city,
    projectName: `负责人保留-${randomUUID()}`,
    customerName: '客户',
    industryCode: 'C25',
    customerNeed: '验证账号停用不隐藏责任信息',
    totalAmount: 100,
    provinceLeaderUserId: leader.id,
    cityAccountManagerName: '客户经理',
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: 50 }],
    initialProgress: '完成交流',
    nextAction: '继续沟通',
    confirmNew: true,
    duplicateReason: '隔离责任人验收',
  });
  const disabled = await request.patch(
    `http://127.0.0.1:18080/api/v1/admin/users/${leader.id}/enabled`,
    {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      data: { version: leader.version, enabled: false },
    },
  );
  expect(disabled.ok(), await disabled.text()).toBeTruthy();
  await page.goto(`/projects/${project.id}`);
  await chooseActor(page, ids.reporter);
  await expect(page.getByText(name, { exact: true })).toBeVisible();
  const list = await request.get(
    `http://127.0.0.1:18080/api/v1/projects?provinceLeaderName=${encodeURIComponent(name)}`,
    {
      headers: { 'X-NDI-Actor-Id': ids.reporter },
    },
  );
  expect(list.ok(), await list.text()).toBeTruthy();
  const rows = (await list.json()).data.items as { id: string; provinceLeaderName: string }[];
  expect(rows.find((row) => row.id === project.id)?.provinceLeaderName).toBe(name);
  const form = page
    .getByRole('heading', { name: '普通信息维护', exact: true })
    .locator('..')
    .locator('..');
  await form.getByRole('button', { name: '编辑', exact: true }).click();
  await expect(form.getByRole('combobox', { name: '省分负责人', exact: true })).toHaveValue(
    leader.id,
  );
  await form.getByRole('button', { name: '保存普通信息', exact: true }).click();
  await expect(form.getByRole('alert')).toContainText('省分负责人');
  await form.getByRole('combobox', { name: '省分负责人', exact: true }).selectOption(ids.leader);
  await form.getByRole('button', { name: '保存普通信息', exact: true }).click();
  await expect(page.getByText('演示省分负责人', { exact: true }).first()).toBeVisible();
});
