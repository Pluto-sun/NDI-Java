import { sha256 } from '@noble/hashes/sha2.js';

/** HTTP 的普通 IP 页面没有 SubtleCrypto，使用本地摘要实现保持附件校验一致。 */
export function sha256Hex(bytes: ArrayBuffer): string {
  return Array.from(sha256(new Uint8Array(bytes)), (value) =>
    value.toString(16).padStart(2, '0'),
  ).join('');
}

/** getRandomValues 不要求安全上下文；保持 UUID v4 的随机性和格式。 */
export function requestUuid(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
