import { test, expect } from '@playwright/test';
import { chooseActor, ids } from './support/projects';

test('七个模块、多级菜单、占位页及个人中心可用', async ({ page }, testInfo) => {
  await page.goto('/workbench');
  await expect(page.getByRole('img', { name: '联通格物', exact: true })).toBeVisible();
  await expect(page).toHaveTitle('联通格物 · 运营管理平台');
  await chooseActor(page, ids.admin);
  const nav = page.getByRole('navigation', { name: '模块导航', exact: true });
  await expect(nav.locator('.nav-module')).toHaveCount(7);
  for (const title of [
    '驾驶舱',
    '项目管理',
    '计收管理',
    '排产管理',
    '能力指标管理',
    '消息与审批',
    '个人中心',
  ]) {
    await expect(nav.getByRole('button', { name: title, exact: true })).toBeVisible();
  }
  for (const [link, title] of [
    ['计收驾驶舱', '计收驾驶舱'],
    ['排产驾驶舱', '排产驾驶舱'],
    ['能力指标驾驶舱', '能力指标驾驶舱'],
    ['计收台账', '计收管理'],
    ['排产计划', '排产计划'],
    ['能力指标', '能力指标管理'],
  ]) {
    await nav.getByRole('link', { name: link, exact: true }).click();
    await expect(page.getByRole('heading', { name: title, exact: true, level: 1 })).toBeVisible();
  }
  await nav.getByRole('link', { name: '个人设置', exact: true }).click();
  await expect(page.getByLabel('姓名', { exact: true })).toBeVisible();
  await page.getByLabel('姓名', { exact: true }).fill('模块导航验收管理员');
  await page.getByRole('button', { name: '保存资料', exact: true }).click();
  await expect(page.getByText('个人资料已保存。', { exact: true })).toBeVisible();
  await expect(page.locator('.account')).toContainText('模块导航验收管理员');
  await nav.getByRole('link', { name: '接口人范围', exact: true }).click();
  await expect(page.getByRole('heading', { name: '接口人范围', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '配置', exact: true }).first().click();
  await expect(page.getByRole('checkbox', { name: /^北京市/ }).first()).toBeVisible();
  await expect(page.getByRole('checkbox', { name: /^新疆维吾尔自治区/ }).first()).toBeVisible();
  await expect(page.getByRole('checkbox', { name: /^台湾省/ }).first()).toBeVisible();
  await page.getByRole('button', { name: '关闭配置接口人范围', exact: true }).click();
  await nav.getByRole('link', { name: '账号与组织', exact: true }).click();
  const trigger = page.getByRole('button', { name: '新建账号', exact: true });
  await trigger.click();
  await expect(page.getByRole('dialog', { name: '创建账号', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await expect(page.getByRole('button', { name: '新增地市', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '新增省分', exact: true })).toHaveCount(0);
  await trigger.click();
  const modal = page.getByRole('dialog', { name: '创建账号', exact: true });
  await expect(modal).toBeVisible();
  await modal.getByRole('combobox', { name: '所属省分', exact: true }).click();
  await expect(page.getByRole('listbox', { name: '可选项', exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('themed-select-modal.png'), fullPage: false });
  await page
    .getByRole('listbox', { name: '可选项', exact: true })
    .getByRole('option', { name: '北京市', exact: true })
    .click();
  await expect(modal.getByRole('combobox', { name: '所属省分', exact: true })).not.toHaveValue('');
  await expect(page.getByRole('listbox')).toHaveCount(0);
  await modal.getByRole('combobox', { name: '所属省分', exact: true }).press('ArrowDown');
  await expect(page.getByRole('listbox')).toBeVisible();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: '关闭创建账号', exact: true }).click();
  await nav.getByRole('link', { name: '项目管理驾驶舱', exact: true }).click();
  await expect(page.getByRole('button', { name: '刷新驾驶舱', exact: true })).toBeEnabled();
  await expect(page.locator('.dashboard-kpi')).toHaveCount(5);
  await expect(page.locator('.dev-banner')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('module-navigation.png'), fullPage: true });
});
