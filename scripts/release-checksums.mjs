import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const root = process.argv[2];
if (!root) throw new Error('缺少交付目录');
const entries = [];
async function visit(directory, prefix = '.') {
  for (const item of (await readdir(directory, { withFileTypes: true })).sort((a, b) =>
    a.name.localeCompare(b.name, 'en'),
  )) {
    const relative = `${prefix}/${item.name}`;
    if (!/^[A-Za-z0-9._/-]+$/.test(relative)) throw new Error(`不支持的交付文件名：${relative}`);
    const path = join(directory, item.name);
    if (item.isDirectory()) await visit(path, relative);
    else if (item.isFile() && relative !== './SHA256SUMS')
      entries.push(`${createHash('sha256').update(await readFile(path)).digest('hex')}  ${relative}`);
    else throw new Error(`交付包不允许符号链接或特殊文件：${relative}`);
  }
}
await visit(root);
await writeFile(join(root, 'SHA256SUMS'), entries.join('\n') + '\n');
