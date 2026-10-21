import { randomInt, randomUUID } from 'node:crypto';
import { test, expect, type APIRequestContext } from '@playwright/test';
import { ids } from '../support/projects';

async function mutate<T>(request: APIRequestContext, path: string, data: unknown): Promise<T> {
  const csrf = await request.get('/api/v1/auth/csrf');
  const response = await request.post(`/api/v1${path}`, {
    headers: { 'X-XSRF-TOKEN': (await csrf.json()).data.token },
    data,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as T;
}

test('管理员确认演练三个督办层级、重复发送去重，业务账号只读审计并通过进展闭环', async ({
  page,
  browser,
}, testInfo) => {
  await page.goto('/admin/inactivity');
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '督办管理', exact: true })).toBeVisible();
  const request = page.context().request;
  const phone = `139${randomInt(10000000, 99999999)}`;
  const password = 'Inactivity-E2e-Password!';
  await mutate(request, '/admin/users', {
    account: {
      loginName: phone,
      displayName: '督办验收地市',
      email: '',
      roleCodes: ['CITY_REPORTER'],
      provinceId: ids.province,
      cityId: ids.city,
    },
    password,
  });
  const city = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  try {
    await mutate(city.request, '/auth/sessions', { loginName: phone, password });
    const name = `督办管理验收-${randomUUID()}`;
    const project = await mutate<{ id: string }>(city.request, '/projects', {
      provinceId: ids.province,
      cityId: ids.city,
      projectName: name,
      customerName: '验收客户',
      industryCode: 'C25',
      customerNeed: '验证管理员演练与审计',
      totalAmount: '100.00',
      provinceLeaderUserId: ids.leader,
      cityAccountManagerName: '客户经理',
      categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50.00' }],
      initialProgress: '首次交流',
      nextAction: '跟进需求',
      confirmNew: true,
      duplicateReason: '独立验收',
    });
    const before = (await (await city.request.get(`/api/v1/projects/${project.id}`)).json()).data;
    await expect(page.getByRole('button', { name: '手动发起督办', exact: true })).toBeDisabled();
    await page.getByLabel('项目名称', { exact: true }).fill(name);
    await page.getByRole('button', { name: '查询督办项目', exact: true }).click();
    const select = page.getByRole('combobox', { name: '测试项目', exact: true });
    await expect(select.locator('option')).toHaveCount(2);
    await select.selectOption(project.id);
    let writes = 0;
    let requests = 0;
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().endsWith('/inactivity-reminders/trigger'))
        requests++;
    });
    page.on('response', (response) => {
      if (
        response.request().method() === 'POST' &&
        response.url().endsWith('/inactivity-reminders/trigger') &&
        response.ok()
      )
        writes++;
    });
    await page.getByRole('button', { name: '手动发起督办', exact: true }).click();
    await expect(page.getByRole('heading', { name: '确认发送督办', exact: true })).toBeFocused();
    await page.getByRole('button', { name: '取消发送', exact: true }).click();
    expect(requests).toBe(0);
    for (const [index, days] of [30, 30, 33, 36].entries()) {
      await page
        .getByRole('combobox', { name: '模拟未变动天数', exact: true })
        .selectOption(String(days));
      await page.getByRole('button', { name: '手动发起督办', exact: true }).click();
      await page.getByRole('button', { name: '确认发送', exact: true }).click();
      if (index === 1)
        await expect(
          page.getByText('本次未发送：同一周期的该层级提醒已发送，或督办已处理。', { exact: true }),
        ).toBeVisible();
      else await expect(page.getByText(/^督办已发送，接收人数：/)).toBeVisible();
    }
    expect(writes).toBe(4);
    const audit = page.getByRole('region', { name: '督办审计记录', exact: true });
    await expect(audit.getByText('管理员手动演练', { exact: true })).toHaveCount(4);
    for (const text of ['通知省分负责人', '通知接口人', '通知集团'])
      await expect(audit.getByText(text, { exact: true })).toBeVisible();
    const after = (await (await city.request.get(`/api/v1/projects/${project.id}`)).json()).data;
    expect(after.updatedAt).toBe(before.updatedAt);
    expect(after.versionNo).toBe(before.versionNo);
    await page.screenshot({
      path: testInfo.outputPath('inactivity-administration.png'),
      fullPage: true,
    });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy();

    const csrf = (await (await city.request.get('/api/v1/auth/csrf')).json()).data.token;
    const denied = await city.request.post('/api/v1/projects/admin/inactivity-reminders/trigger', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { projectId: project.id, days: 30 },
    });
    expect(denied.status()).toBe(403);
    await mutate(city.request, `/projects/${project.id}/progress`, {
      progressContent: '客户反馈已跟进',
      nextAction: '继续方案交流',
    });
    const userPage = await city.newPage();
    await userPage.goto('/admin/inactivity');
    await expect(userPage).toHaveURL(/\/projects$/);
    await expect(userPage.getByRole('link', { name: '督办管理', exact: true })).toHaveCount(0);
    await userPage.goto(`/projects/${project.id}`);
    await expect(
      userPage
        .getByRole('region', { name: '督办审计记录', exact: true })
        .getByText('督办闭环', { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  } finally {
    await city.close();
  }
});
