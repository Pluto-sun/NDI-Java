const MAX_EXCEL_BYTES = 20 * 1024 * 1024;

export function validateExcelUpload(file: File) {
  if (!file.size || file.size > MAX_EXCEL_BYTES) {
    throw new Error('Excel 文件须为 1 字节至 20 MB');
  }
}

/** 浏览器对 keepalive 请求有 64 KiB 的合计发送上限。 */
export function importTaskBody(value: unknown, signal?: AbortSignal): RequestInit {
  const body = JSON.stringify(value);
  return {
    body,
    signal: signal ?? AbortSignal.timeout(10 * 60_000),
    keepalive: !signal && new TextEncoder().encode(body).byteLength < 64 * 1024,
  };
}
