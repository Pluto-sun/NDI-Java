import { verifyStickyAction } from './support/stickyActions';
import { createHash, randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, ids } from './support/projects';
import { importWorkbook } from './support/importWorkbook';

const admin = { 'X-NDI-Actor-Id': ids.admin };
const group = '77777777-7777-4777-8777-777777777777';
const groupHeaders = { 'X-NDI-Actor-Id': group };
const city = { 'X-NDI-Actor-Id': ids.reporter };
const api = 'http://127.0.0.1:18080/api/v1/capability/';
const province = '本地演示省';

for (const [kind, fields] of [
  [
    'wangwu',
    { 部署地点: '广东省', 具体名称: '万悟验收', 部署进度: '已部署', 版本更新日期: '2026.04.14' },
  ],
  [
    'agents-live',
    {
      省分智能化接口人: '省分接口',
      环节: '研发设计',
      应用项目名称: '测试应用项目',
      最终客户名称: '测试最终客户',
      智能体功能简述: '生产报表',
      是否为三方生态迁移: '否',
      是否计收: '已计收',
      是否在计收表中出现: '否',
    },
  ],
  [
    'agents-development',
    {
      环节: '2-生产制造',
      状态: '待开发',
      智能体功能简述: '辅助处理服务请求',
      开发进度: '10.00%',
      智能体开发开始时间: '2026-07',
      是否计收: '已计收',
    },
  ],
] as const) {
  test(`${kind} 新增、修改、筛选、Excel 和更新时间`, async ({ page, request }) => {
    const reviewer = '验收-' + randomUUID();
    const root = api + kind;
    const row = {
      ...fields,
      接口人: reviewer,
      省分: province,
      ...(kind === 'wangwu' ? { 具体名称: reviewer } : {}),
      ...(kind === 'agents-live' ? { 智能体名称: reviewer } : {}),
    };
    const input = { provinceId: ids.province, fields: row };
    const ref = (await (await request.get(root + '/reference-data', { headers: admin })).json())
      .data;
    const outside = ref.provinces.find((option: { id: string }) => option.id !== ids.province);
    expect(
      (
        await request.post(root, { headers: city, data: { ...input, provinceId: outside.id } })
      ).status(),
    ).toBe(403);
    const created = await request.post(root, { headers: groupHeaders, data: input });
    expect(created.ok(), await created.text()).toBeTruthy();
    const first = (await created.json()).data;
    expect((await request.get(root + '/' + first.id, { headers: city })).status()).toBe(404);
    const edited = await request.put(root + '/' + first.id, {
      headers: admin,
      data: {
        version: first.version,
        entry:
          kind === 'agents-live' ? { ...input, fields: { ...row, 智能体包含组件情况: '' } } : input,
      },
    });
    expect(edited.ok(), await edited.text()).toBeTruthy();
    const second = (await edited.json()).data;
    expect(Date.parse(second.updatedAt)).toBeGreaterThan(Date.parse(first.updatedAt));
    if (kind === 'agents-development') {
      expect(ref.options['环节']).toEqual(['运营管理', '研发设计', '营销服务', '生产制造']);
      expect(ref.options['状态']).toEqual(['开发中', '已完成', '待开发']);
      for (const invalid of [
        { 开发进度: '101%' },
        { 智能体开发开始时间: '7月' },
        { 状态: '营销服务' },
      ]) {
        const response = await request.put(root + '/' + first.id, {
          headers: admin,
          data: { version: second.version, entry: { ...input, fields: { ...row, ...invalid } } },
        });
        expect(response.status()).toBe(400);
      }
    }
    if (kind === 'agents-live') {
      expect((await request.post(root, { headers: admin, data: input })).status()).toBe(409);
      for (const field of ['环节', '智能体名称', '应用项目名称', '最终客户名称', '是否计收']) {
        expect(ref.required).toContain(field);
        const rejected = await request.post(root, {
          headers: admin,
          data: { ...input, fields: { ...row, [field]: '' } },
        });
        expect(rejected.status()).toBe(400);
        expect(await rejected.text()).toContain(field);
      }
      const tokenPath = root + '/' + first.id + '/tokens';
      const saved = await request.post(tokenPath, {
        headers: admin,
        data: { month: '2026-07', amount: '0' },
      });
      expect(saved.ok(), await saved.text()).toBeTruthy();
      const token = (await saved.json()).data;
      const afterToken = (
        await (await request.get(root + '/' + first.id, { headers: admin })).json()
      ).data;
      expect(Date.parse(afterToken.updatedAt)).toBeGreaterThan(Date.parse(second.updatedAt));
      expect(
        (
          await request.post(tokenPath, { headers: admin, data: { month: '2026-07', amount: '1' } })
        ).status(),
      ).toBe(409);
      const list = (await (await request.get(tokenPath, { headers: admin })).json()).data;
      expect(list).toEqual([{ id: token, month: '2026-07', amount: '0', screenshotId: null }]);
      const shot = tokenPath + '/' + token + '/screenshot';
      expect((await request.get(shot, { headers: city })).status()).toBe(404);
      const png = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lfoAAAAASUVORK5CYII=',
        'base64',
      );
      const init = await request.post(shot, {
        headers: admin,
        data: {
          originalName: 'token.png',
          mediaType: 'image/png',
          byteSize: png.length,
          sha256Hex: createHash('sha256').update(png).digest('hex'),
        },
      });
      expect(init.ok(), await init.text()).toBeTruthy();
      const intent = (await init.json()).data;
      expect(
        (await request.put(intent.uploadUrl, { headers: intent.requiredHeaders, data: png })).ok(),
      ).toBeTruthy();
      expect(
        (await request.post(shot + '/' + intent.documentId, { headers: admin })).ok(),
      ).toBeTruthy();
      const afterShot = (
        await (await request.get(root + '/' + first.id, { headers: admin })).json()
      ).data;
      expect(Date.parse(afterShot.updatedAt)).toBeGreaterThan(Date.parse(afterToken.updatedAt));
      const download = (await (await request.get(shot, { headers: admin })).json()).data;
      expect((await request.get(download.downloadUrl)).ok()).toBeTruthy();
      expect(
        (await (await request.get(root + '/' + first.id, { headers: admin })).json()).data
          .updatedAt,
      ).toBe(afterShot.updatedAt);
    }
    await page.goto('/capability/' + kind);
    await chooseActor(page, group);
    await expect(page.getByRole('button', { name: '新增记录', exact: true })).toBeVisible();
    if (kind === 'agents-live') {
      await page.goto('/capability/agents-live/' + first.id);
      await page.getByRole('button', { name: '查看截图', exact: true }).click();
      const screenshot = page.getByRole('dialog', { name: '月度截图' });
      await expect(screenshot.getByRole('img')).toBeVisible();
      await page.keyboard.press('Escape');
      const downloaded = page.waitForEvent('download');
      await page.getByRole('button', { name: '下载截图', exact: true }).click();
      expect((await downloaded).suggestedFilename()).toBe('token.png');
      await page.goto('/capability/' + kind);
    }

    await page.getByRole('link', { name: 'Excel 导入', exact: true }).click();
    if (kind === 'agents-live') {
      const tabs = page.getByRole('group', { name: '导入内容' }).getByRole('button');
      const tabNames = ['Excel 已上线智能体指标追踪导入', '月度 Token 导入'];
      await expect(tabs).toHaveText(tabNames);
      await tabs.nth(1).click();
      await expect(tabs).toHaveText(tabNames);
      await expect(tabs.nth(1)).toHaveAttribute('aria-pressed', 'true');
      await tabs.nth(0).click();
      await expect(tabs).toHaveText(tabNames);
      await expect(tabs.nth(0)).toHaveAttribute('aria-pressed', 'true');
    }
    const legacyLiveExcelRow = {
      ...Object.fromEntries(Object.entries(row).filter(([key]) => key !== '是否在计收表中出现')),
      是否在子辉的计收表出现: '否',
      应用项目名称: 'Excel 应用项目',
    };
    await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
      name: '指标.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: importWorkbook(
        kind === 'agents-development'
          ? [
              { ...row, 环节: '4-营销服务' },
              { ...row, 智能体开发开始时间: '7月' },
              { ...row, 开发进度: '101%' },
            ]
          : kind === 'wangwu'
            ? [{ ...row, 具体名称: reviewer + '-导入' }, { ...row, 部署地点: '' }, row]
            : [legacyLiveExcelRow, { ...legacyLiveExcelRow, 应用项目名称: '测试应用项目' }],
      ),
    });
    await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
    await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
    if (kind === 'agents-live') {
      await expect(page.getByText(/系统内已存在相同业务唯一键/)).toBeVisible();
    }
    if (kind === 'wangwu') {
      await expect(page.getByText(/部署地点不能为空/)).toBeVisible();
      await expect(page.getByText(/系统内已存在相同业务唯一键/)).toBeVisible();
      await page.getByRole('button', { name: '查看／修正第 3 行' }).click();
      const correction = page.getByRole('dialog');
      await expect(correction.getByRole('textbox', { name: '部署地点' })).toHaveValue('广东省');
      await expect(correction.getByRole('combobox', { name: '部署进度' })).toBeVisible();
      await page.keyboard.press('Escape');
    }
    if (kind === 'agents-development') {
      await expect(page.getByText(/智能体开发开始时间须填写年份和月份/)).toBeVisible();
      await expect(page.getByText(/开发进度须在 0–100%/)).toBeVisible();
      const failureExport = page.waitForEvent('download');
      await page.getByRole('button', { name: '导出预检失败原始数据' }).click();
      expect((await failureExport).suggestedFilename()).toMatch(/导入预检失败原始数据\.xlsx$/);
      await page.getByRole('button', { name: '查看／修正第 3 行' }).click();
      const correction = page.getByRole('dialog');
      await expect(correction.getByRole('combobox', { name: '状态', exact: true })).toHaveValue(
        '待开发',
      );
      await expect(correction.getByRole('combobox', { name: '环节' })).toBeVisible();
      await expect(
        correction
          .getByRole('combobox', { name: '环节', exact: true })
          .getByRole('option', { name: '生产制造', exact: true }),
      ).toBeAttached();
      await page.keyboard.press('Escape');
      expect(
        (await (await request.get(root + '/' + first.id, { headers: admin })).json()).data
          .updatedAt,
      ).toBe(second.updatedAt);
    }
    await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
    await expect(page.getByRole('heading', { name: '导入任务已完成' })).toBeVisible();
    await page.goto('/capability/' + kind);
    await page.getByLabel('接口人', { exact: true }).fill(reviewer);
    if (kind === 'wangwu') {
      await page.getByRole('textbox', { name: '部署地点', exact: true }).fill('广东省');
    }
    await page.getByRole('button', { name: '查询', exact: true }).click();
    const matching = page.locator('tbody tr').filter({ hasText: reviewer });
    await expect(matching).toHaveCount(2);
    if (kind !== 'wangwu') {
      await expect(page.getByRole('columnheader', { name: '智能体功能简述' })).toHaveCount(0);
    }
    if (kind === 'agents-live') {
      await expect(matching.filter({ hasText: 'Excel 应用项目' })).toHaveCount(1);
      await expect(matching.filter({ hasText: '测试应用项目' })).toHaveCount(1);
    }
    if (kind === 'agents-development') {
      await expect(matching.filter({ hasText: '营销服务' })).toHaveCount(1);
      await expect(matching.filter({ hasText: '待开发' })).toHaveCount(2);
    }
    await expect(matching.first()).toContainText(/\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/);
    const edit = matching.first().getByRole('button', { name: '编辑', exact: true });
    await verifyStickyAction(page.getByRole('region', { name: '能力指标明细' }), edit);
    await edit.click();
    const listEditor = page.getByRole('dialog', { name: '编辑记录' });
    await expect(listEditor.getByRole('group')).toBeVisible();
    if (kind !== 'wangwu') {
      await expect(listEditor.getByRole('textbox', { name: '智能体功能简述' })).toHaveValue(
        fields.智能体功能简述,
      );
    }
    await listEditor
      .getByRole('link', { name: kind === 'agents-live' ? '月度 Token 记录' : '查看记录' })
      .click();
    await expect(page.getByText('更新时间（北京时间）', { exact: true })).toBeVisible();
    if (kind === 'agents-live') {
      await expect(page.getByText('是否在计收表中出现', { exact: true })).toBeVisible();
    }
    if (kind !== 'wangwu') {
      const summary = page.locator('dl > div').filter({ hasText: '智能体功能简述' });
      await expect(summary.locator('dd')).toHaveText(fields.智能体功能简述);
    }
    await page.getByRole('link', { name: '返回列表' }).click();
    await edit.click();
    const editor = page.getByRole('dialog', { name: '编辑记录' });
    await editor.getByLabel('接口人', { exact: true }).fill(reviewer + '-更新');
    await editor.getByRole('button', { name: '保存记录' }).click();
    await expect(editor).toHaveCount(0);
    await page.getByLabel('接口人', { exact: true }).fill(reviewer + '-更新');
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page.locator('tbody tr').filter({ hasText: reviewer + '-更新' })).toHaveCount(1);
    if (kind === 'wangwu')
      await expect(page.locator('tbody tr').filter({ hasText: '2026-04-14' })).toHaveCount(1);
  });
}
