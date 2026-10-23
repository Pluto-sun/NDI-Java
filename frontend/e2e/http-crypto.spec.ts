import { expect, test } from '@playwright/test';
import { createHash } from 'node:crypto';
import { sha256Hex, requestUuid } from '../src/shared/util/crypto';

test('HTTP 兼容摘要覆盖空文件、文本及 20 MB 二进制数据', () => {
  for (const bytes of [
    new Uint8Array(),
    new TextEncoder().encode('中文 abc'),
    new Uint8Array(20 * 1024 * 1024).fill(139),
  ]) {
    expect(sha256Hex(bytes.buffer as ArrayBuffer)).toBe(
      createHash('sha256').update(bytes).digest('hex'),
    );
  }
});

test('没有 SubtleCrypto 或 randomUUID 时仍使用安全随机数生成 UUID', () => {
  const previous = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  const getRandomValues = globalThis.crypto.getRandomValues.bind(globalThis.crypto);
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: { getRandomValues } });
  try {
    const ids = Array.from({ length: 1000 }, () => requestUuid());
    expect(new Set(ids).size).toBe(1000);
    for (const id of ids)
      expect(id).toMatch(/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
    expect(sha256Hex(new TextEncoder().encode('abc').buffer)).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    );
  } finally {
    if (previous) Object.defineProperty(globalThis, 'crypto', previous);
    else Reflect.deleteProperty(globalThis, 'crypto');
  }
});

test('普通 HTTP IP 页面的真实浏览器可计算摘要并生成请求编号', async ({ page, request }) => {
  // 仅代理本地工具模块，不访问该示例 IP，也不连接业务接口或数据库。
  await page.route('http://192.0.2.10/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/') {
      await route.fulfill({
        contentType: 'text/html',
        body: `<script type="module">import { sha256Hex, requestUuid } from '/src/shared/util/crypto.ts'; window.httpCrypto = {sha256Hex, requestUuid};</script>`,
      });
    } else {
      const response = await request.get(`http://127.0.0.1:15173${url.pathname}${url.search}`);
      await route.fulfill({ response });
    }
  });
  await page.goto('http://192.0.2.10/');
  await page.waitForFunction(() => 'httpCrypto' in window);
  const result = await page.evaluate(() => {
    const helpers = (
      window as unknown as {
        httpCrypto: { sha256Hex: (b: ArrayBuffer) => string; requestUuid: () => string };
      }
    ).httpCrypto;
    return {
      secure: isSecureContext,
      subtle: typeof crypto.subtle,
      digest: helpers.sha256Hex(new TextEncoder().encode('abc').buffer),
      id: helpers.requestUuid(),
    };
  });
  expect(result.secure).toBe(false);
  expect(result.subtle).toBe('undefined');
  expect(result.digest).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  expect(result.id).toMatch(
    /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/,
  );
});
