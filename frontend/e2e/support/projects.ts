import { expect, type APIRequestContext, type Page } from '@playwright/test';

const apiRoot = 'http://127.0.0.1:18080/api/v1/projects';
export const ids = {
  province: '11111111-1111-4111-8111-111111111111',
  city: '22222222-2222-4222-8222-222222222222',
  leader: '33333333-3333-4333-8333-333333333333',
  reporter: '44444444-4444-4444-8444-444444444444',
  admin: '66666666-6666-4666-8666-666666666666',
};

export async function post<T>(
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

export async function get<T>(request: APIRequestContext, actor: string, path: string): Promise<T> {
  const response = await request.get(`${apiRoot}${path}`, { headers: { 'X-NDI-Actor-Id': actor } });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as T;
}

export async function createProject(request: APIRequestContext, projectName: string) {
  return post<{ id: string }>(request, ids.reporter, '', {
    provinceId: ids.province,
    cityId: ids.city,
    projectName,
    customerName: '验收客户',
    industryCode: 'C25',
    customerNeed: '验证项目管理全流程',
    totalAmount: '100.00',
    provinceLeaderUserId: ids.leader,
    cityAccountManagerName: '验收客户经理',
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50.00' }],
    initialProgress: '已开展客户交流',
    nextAction: '跟进客户反馈',
    confirmNew: true,
    duplicateReason: '隔离浏览器验收的独立项目',
  });
}

export async function chooseActor(page: Page, actorId: string) {
  await page.getByLabel('账号', { exact: true }).selectOption(actorId);
  await page.getByRole('button', { name: '进入项目管理', exact: true }).click();
}
