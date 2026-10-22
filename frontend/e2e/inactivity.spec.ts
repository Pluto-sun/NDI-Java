import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { ids, post, get, createProject, chooseActor } from './support/projects';
import type { NotificationCounts } from '../src/features/projects/types';

const { leader, reporter, admin } = ids;

test('督办从省分转发地市，追加进展后在历史中闭环', async ({ page, request }, testInfo) => {
  const projectName = `浏览器督办验收-${randomUUID()}`;
  const project = await createProject(request, projectName);
  await post(request, admin, '/admin/inactivity-reminders/trigger', {
    projectId: project.id,
    days: 30,
  });
  await page.goto('/notifications');
  await chooseActor(page, leader);
  await expect(page.getByRole('heading', { name: '消息通知', exact: true })).toBeVisible();
  const card = page.locator('.inactivity-card').filter({ hasText: projectName });
  const countsBefore = await get<NotificationCounts>(request, leader, '/notification-counts');
  await page.route('**/inactivity-cases/*/read', (route) =>
    route.fulfill({ status: 503, body: '{}' }),
  );
  await card.getByRole('button', { name: '标记督办已读', exact: true }).click();
  await expect(card.getByRole('alert')).toBeVisible();
  await page.unroute('**/inactivity-cases/*/read');
  await card.getByRole('button', { name: '标记督办已读', exact: true }).click();
  await expect(card).toContainText('已读，仍待处理');
  await expect(card).toContainText('待处理');
  const countsAfter = await get<NotificationCounts>(request, leader, '/notification-counts');
  expect(countsAfter.count).toBe(countsBefore.count - 1);
  expect(countsAfter.inactivityUnreadCount).toBe(countsBefore.inactivityUnreadCount - 1);
  expect(countsAfter.inactivityPendingCount).toBe(countsBefore.inactivityPendingCount);
  await page.reload();
  await expect(card).toContainText('已读，仍待处理');
  await expect(card.getByRole('button', { name: '标记督办已读', exact: true })).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath('inactivity-read-pending.png'),
    fullPage: true,
  });
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
  await chooseActor(page, reporter);
  await expect(card).toContainText('待处理');
  await expect(card.getByRole('button', { name: '标记督办已读', exact: true })).toBeVisible();
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
