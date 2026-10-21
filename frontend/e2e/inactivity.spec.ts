import { randomUUID } from 'node:crypto';
import { test, expect, type APIRequestContext } from '@playwright/test';

const apiRoot = 'http://127.0.0.1:18080/api/v1/projects';
const province = '11111111-1111-4111-8111-111111111111';
const city = '22222222-2222-4222-8222-222222222222';
const leader = '33333333-3333-4333-8333-333333333333';
const reporter = '44444444-4444-4444-8444-444444444444';
const admin = '66666666-6666-4666-8666-666666666666';

async function post<T>(
  request: APIRequestContext,
  actor: string,
  path: string,
  data: unknown,
): Promise<T> {
  const response = await request.post(`${apiRoot}${path}`, {
    headers: { 'X-NDI-Actor-Id': actor },
    data,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as T;
}

test('督办从省分转发地市，追加进展后在历史中闭环', async ({ page, request }, testInfo) => {
  const projectName = `浏览器督办验收-${randomUUID()}`;
  const project = await post<{ id: string }>(request, reporter, '', {
    provinceId: province,
    cityId: city,
    projectName,
    customerName: '验收客户',
    industryCode: 'C25',
    customerNeed: '验证项目督办全流程',
    totalAmount: '100.00',
    provinceLeaderUserId: leader,
    cityAccountManagerName: '验收客户经理',
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50.00' }],
    initialProgress: '已开展客户交流',
    nextAction: '跟进客户反馈',
    confirmNew: true,
    duplicateReason: '隔离浏览器验收的独立项目',
  });
  await post(request, admin, '/admin/inactivity-reminders/trigger', {
    projectId: project.id,
    days: 30,
  });
  await page.goto('/notifications');
  await page.getByLabel('账号', { exact: true }).selectOption(leader);
  await page.getByRole('button', { name: '进入项目管理', exact: true }).click();
  await expect(page.getByRole('heading', { name: '消息通知', exact: true })).toBeVisible();
  const card = page.locator('.inactivity-card').filter({ hasText: projectName });
  await card.getByRole('button', { name: '转发督办', exact: true }).click();
  await card.getByRole('combobox', { name: '接收人', exact: true }).selectOption(reporter);
  await card.getByLabel('督办备注', { exact: true }).fill('请更新本周客户交流进展');
  await card.getByRole('button', { name: '发送督办', exact: true }).click();
  await expect(page.getByText('督办已转发，处理记录已保留。', { exact: true })).toBeVisible();
  await expect(card).toHaveCount(0);
  await page.getByRole('button', { name: '处理历史', exact: true }).click();
  await expect(card).toContainText('已转发');
  await expect(card).toContainText('请更新本周客户交流进展');

  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await page.getByLabel('账号', { exact: true }).selectOption(reporter);
  await page.getByRole('button', { name: '进入项目管理', exact: true }).click();
  await expect(card).toContainText('待处理');
  await expect(card.getByRole('button', { name: '转发督办', exact: true })).toHaveCount(0);
  await card.getByRole('link').click();
  await expect(page.getByRole('heading', { name: projectName, exact: true })).toBeVisible();
  await page.getByLabel('进展内容', { exact: true }).fill('客户确认下一轮方案交流时间');
  await page.getByLabel('下一步动作', { exact: true }).fill('准备交流材料');
  await page.getByRole('button', { name: '追加进展', exact: true }).click();
  await expect(page.getByText('项目进展已保存。', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: '消息通知', exact: true }).click();
  await expect(card).toHaveCount(0);
  await page.getByRole('button', { name: '处理历史', exact: true }).click();
  await expect(card).toContainText('已闭环');
  await expect(card).toContainText('项目发生有效更新，督办闭环');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('inactivity-resolved.png'), fullPage: true });
});
