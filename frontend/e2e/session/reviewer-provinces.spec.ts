import { randomInt, randomUUID } from 'node:crypto';
import { expect, test, type APIRequestContext, type BrowserContext } from '@playwright/test';

const root = '/api/v1';
const a = {
  province: '11111111-1111-4111-8111-111111111111',
  city: '22222222-2222-4222-8222-222222222222',
  leader: '33333333-3333-4333-8333-333333333333',
};
const b = {
  province: '88888888-8888-4888-8888-888888888888',
  city: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
};
const c = {
  province: '99999999-9999-4999-8999-999999999999',
  city: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
};
const password = 'scope-e2e-password';

async function mutate<T>(
  request: APIRequestContext,
  method: 'POST' | 'PUT',
  path: string,
  data: unknown,
): Promise<T> {
  const csrf = await request.get(`${root}/auth/csrf`);
  const token = (await csrf.json()).data.token;
  const response = await request.fetch(`${root}${path}`, {
    method,
    headers: { 'X-XSRF-TOKEN': token },
    data,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as T;
}
async function login(context: BrowserContext, loginName: string, value = password) {
  await mutate(context.request, 'POST', '/auth/sessions', { loginName, password: value });
}
async function createAccount(
  request: APIRequestContext,
  role: string,
  name: string,
  provinceId: string | null,
  cityId: string | null,
) {
  return mutate<{ id: string; loginName: string; version: number }>(
    request,
    'POST',
    '/admin/users',
    {
      account: {
        loginName: `137${randomInt(10000000, 99999999)}`,
        displayName: name,
        email: '',
        roleCodes: [role],
        provinceId,
        cityId,
      },
      password,
    },
  );
}

test('批量配置两个接口人后只读取负责省份，移除范围撤销会话和既有导出', async ({
  page,
  browser,
}, testInfo) => {
  const nonce = randomUUID().slice(0, 8);
  const administrator = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  const reviewer = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  const creator = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  try {
    await login(administrator, 'e2e_admin', 'e2e-session-password');
    const reviewerResponse = await administrator.request.get(
      `${root}/admin/users?search=local_reviewer`,
    );
    const existing = (await reviewerResponse.json()).data.items[0] as {
      id: string;
      version: number;
    };
    await mutate(administrator.request, 'PUT', `/admin/users/${existing.id}/reviewer-provinces`, {
      version: existing.version,
      provinceIds: [a.province],
    });
    const other = await createAccount(
      administrator.request,
      'INTERFACE_REVIEWER',
      `范围验收接口人-${nonce}`,
      null,
      null,
    );
    const leaderB = await createAccount(
      administrator.request,
      'PROVINCE_REPORTER',
      `乙省负责人-${nonce}`,
      b.province,
      null,
    );
    const leaderC = await createAccount(
      administrator.request,
      'PROVINCE_REPORTER',
      `丙省负责人-${nonce}`,
      c.province,
      null,
    );
    const reporterA = await createAccount(
      administrator.request,
      'CITY_REPORTER',
      `甲市负责人-${nonce}`,
      a.province,
      a.city,
    );
    const reporterB = await createAccount(
      administrator.request,
      'CITY_REPORTER',
      `乙市负责人-${nonce}`,
      b.province,
      b.city,
    );
    const reporterC = await createAccount(
      administrator.request,
      'CITY_REPORTER',
      `丙市负责人-${nonce}`,
      c.province,
      c.city,
    );
    const projectIds: string[] = [];
    const names: string[] = [];
    for (const [index, region] of [a, b, c].entries()) {
      const reporter = [reporterA, reporterB, reporterC][index];
      await login(creator, reporter.loginName);
      const name = `范围浏览器验收-${nonce}-${index}`;
      names.push(name);
      const created = await mutate<{ id: string }>(creator.request, 'POST', '/projects', {
        provinceId: region.province,
        cityId: region.city,
        projectName: name,
        customerName: '范围验收客户',
        industryCode: 'C25',
        customerNeed: '多省权限验收',
        totalAmount: '100',
        provinceLeaderUserId: [a.leader, leaderB.id, leaderC.id][index],
        cityAccountManagerName: '验收经理',
        categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
        initialProgress: '初次交流',
        nextAction: '继续跟进',
        confirmNew: true,
        duplicateReason: '隔离权限验收项目',
      });
      projectIds.push(created.id);
    }

    await page.goto('/admin/reviewer-provinces');
    await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
    await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '接口人范围', exact: true })).toBeVisible();
    const first = page.getByRole('region', { name: '演示省分接口人的负责省份', exact: true });
    const second = page.getByRole('region', {
      name: `范围验收接口人-${nonce}的负责省份`,
      exact: true,
    });
    await first.getByRole('checkbox', { name: '浙江省', exact: true }).check();
    await second.getByRole('checkbox', { name: '浙江省', exact: true }).check();
    await expect(
      page.getByText('当前有 2 位接口人的范围尚未保存。', { exact: false }),
    ).toBeVisible();
    await page.getByRole('button', { name: '批量保存范围', exact: true }).click();
    await expect(
      page.getByText('负责省份已保存，范围变化的接口人需要重新登录。', { exact: true }),
    ).toBeVisible();
    await login(reviewer, 'local_reviewer', 'e2e-session-password');
    const readerPage = await reviewer.newPage();
    await readerPage.goto('/projects');
    await expect(readerPage.getByRole('link', { name: names[0], exact: true })).toBeVisible();
    await expect(readerPage.getByRole('link', { name: names[1], exact: true })).toBeVisible();
    await expect(readerPage.getByRole('link', { name: names[2], exact: true })).toHaveCount(0);
    expect((await reviewer.request.get(`${root}/projects/${projectIds[2]}`)).status()).toBe(403);
    const exportTask = await mutate<{ id: string }>(
      reviewer.request,
      'POST',
      '/projects/exports',
      {},
    );
    await expect
      .poll(
        async () =>
          (await (await reviewer.request.get(`${root}/projects/exports/${exportTask.id}`)).json())
            .data.status,
        { timeout: 15000 },
      )
      .toBe('COMPLETED');
    await first.getByRole('checkbox', { name: '浙江省', exact: true }).uncheck();
    await page.getByRole('button', { name: '批量保存范围', exact: true }).click();
    await expect(
      page.getByText('负责省份已保存，范围变化的接口人需要重新登录。', { exact: true }),
    ).toBeVisible();
    expect((await reviewer.request.get(`${root}/auth/me`)).status()).toBe(401);
    await login(reviewer, 'local_reviewer', 'e2e-session-password');
    await readerPage.reload();
    await expect(readerPage.getByRole('link', { name: names[0], exact: true })).toBeVisible();
    await expect(readerPage.getByRole('link', { name: names[1], exact: true })).toHaveCount(0);
    expect((await reviewer.request.get(`${root}/projects/${projectIds[1]}`)).status()).toBe(403);
    expect(
      (await reviewer.request.get(`${root}/projects/exports/${exportTask.id}/download`)).status(),
    ).toBe(403);
    await login(creator, other.loginName);
    const rows = (await (await creator.request.get(`${root}/projects`)).json()).data.items as {
      id: string;
    }[];
    expect(rows.some((row) => row.id === projectIds[1])).toBeTruthy();
    expect(rows.some((row) => row.id === projectIds[0])).toBeFalsy();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy();
    await expect(page.getByRole('alert')).toHaveCount(0);
    await page.screenshot({ path: testInfo.outputPath('reviewer-provinces.png'), fullPage: true });
  } finally {
    await administrator.close();
    await reviewer.close();
    await creator.close();
  }
});
