import { randomInt, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { ids, post, chooseActor } from './support/projects';

test('未读消息与审批待办独立计数，审批后督办闭环并支持错误恢复', async ({
  page,
  request,
}, testInfo) => {
  async function account(role: string) {
    const response = await request.post('http://127.0.0.1:18080/api/v1/admin/users', {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      data: {
        account: {
          loginName: `138${randomInt(10000000, 99999999)}`,
          displayName: `通知验收-${role}`,
          email: '',
          roleCodes: [role],
          provinceId: ids.province,
          cityId: role === 'CITY_REPORTER' ? ids.city : null,
        },
        password: 'Notification-E2e-2026!',
      },
    });
    expect(response.ok(), await response.text()).toBeTruthy();
    return (await response.json()).data.id as string;
  }
  const leader = await account('PROVINCE_REPORTER');
  const reporter = await account('CITY_REPORTER');
  const project = await post<{ id: string }>(request, reporter, '', {
    provinceId: ids.province,
    cityId: ids.city,
    projectName: `通知闭环-${randomUUID()}`,
    customerName: '通知验收客户',
    industryCode: 'C25',
    customerNeed: '验证消息和审批状态独立更新',
    totalAmount: '100.00',
    provinceLeaderUserId: leader,
    cityAccountManagerName: '客户经理',
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50.00' }],
    initialProgress: '首次交流',
    nextAction: '跟进需求',
    confirmNew: true,
    duplicateReason: '隔离验收',
  });
  await post(request, reporter, `/${project.id}/major-changes`, {
    changeType: 'TOTAL_AMOUNT',
    initiatorLevel: 'CITY',
    approverId: leader,
    reason: '客户调整预算',
    totalAmount: '120.00',
  });
  await post(request, ids.admin, '/admin/inactivity-reminders/trigger', {
    projectId: project.id,
    days: 30,
  });
  await page.goto('/notifications');
  await chooseActor(page, leader);
  const messages = page.getByRole('link', { name: '消息通知', exact: true }).locator('.nav-count');
  const approvals = page.getByRole('link', { name: '审批待办', exact: true }).locator('.nav-count');
  await expect(messages).toHaveText('2');
  await expect(approvals).toHaveText('1');
  await page.getByRole('button', { name: '标记已读', exact: true }).click();
  await expect(messages).toHaveText('1');
  await expect(approvals).toHaveText('1');
  await expect(page.getByText('暂无通知', { exact: true })).toBeVisible();
  await page.getByRole('checkbox', { name: '仅看未读审批通知' }).uncheck();
  await page.getByRole('link', { name: '有新的审批申请', exact: true }).click();
  await page.getByRole('button', { name: '审批通过', exact: true }).click();
  await expect(page.getByText('审批已通过，项目正式数据已更新。', { exact: true })).toBeVisible();
  await expect(messages).toHaveText('0');
  await expect(approvals).toHaveText('0');

  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, reporter);
  await expect(messages).toHaveText('1');
  await expect(approvals).toHaveText('0');
  await page.getByRole('link', { name: '消息通知', exact: true }).click();
  await page.getByRole('link', { name: '申请已通过', exact: true }).click();
  await expect(messages).toHaveText('0');

  await page.route('**/api/v1/projects/notification-counts', (route) =>
    route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }),
  );
  await page.evaluate(() => window.dispatchEvent(new Event('ndi:notification-counts-changed')));
  await expect(messages).toHaveText('!');
  await expect(messages).toHaveAttribute('title', /暂不可用/);
  await page.unroute('**/api/v1/projects/notification-counts');
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(messages).toHaveText('0');
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('notification-counts.png'), fullPage: true });
});
