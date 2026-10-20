import { useEffect, useState, type FormEvent } from 'react';
import { projectsApi } from './api';
import type { DocumentPurpose, ProjectDocument } from './types';
import { Feedback } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';
import { openProjectDocument } from './openDocument';

const purposes: Record<DocumentPurpose, string> = {
  GENERAL: '普通资料',
  CUSTOMER_SOLUTION: '客户侧交流方案',
  AWARD_NOTICE: '中标通知书',
};
const mediaTypes: Record<string, string> = {
  pdf: 'application/pdf',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
};

export function ProjectDocuments({
  projectId,
  readOnly,
  onChanged,
}: {
  projectId: string;
  readOnly: boolean;
  onChanged?: () => void;
}) {
  const [items, setItems] = useState<ProjectDocument[]>([]);
  const [purpose, setPurpose] = useState<DocumentPurpose>('GENERAL');
  const [file, setFile] = useState<File | null>(null);
  const [fileInputKey, setFileInputKey] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    projectsApi
      .documents(projectId)
      .then((result) => {
        if (active) setItems(result);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '资料加载失败');
      });
    return () => {
      active = false;
    };
  }, [projectId]);

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return;
    setError('');
    setMessage('');
    const extension = file.name.split('.').at(-1)?.toLowerCase() ?? '';
    const mediaType = mediaTypes[extension];
    if (!mediaType || file.size < 1 || file.size > 20 * 1024 * 1024) {
      setError('仅支持 20 MB 以内的非空 PDF、DOCX、PPTX、PNG 或 JPG 文件。');
      return;
    }
    setBusy(true);
    try {
      const bytes = await file.arrayBuffer();
      const digest = await crypto.subtle.digest('SHA-256', bytes);
      const sha256Hex = Array.from(new Uint8Array(digest), (value) =>
        value.toString(16).padStart(2, '0'),
      ).join('');
      const intent = await projectsApi.beginUpload(projectId, {
        purpose,
        originalName: file.name,
        mediaType,
        byteSize: file.size,
        sha256Hex,
      });
      const response = await fetch(intent.uploadUrl, {
        method: intent.method,
        headers: intent.requiredHeaders,
        body: file,
      });
      if (!response.ok) throw new Error(`对象存储上传失败（${response.status}）`);
      await projectsApi.completeUpload(projectId, intent.documentId);
      setItems(await projectsApi.documents(projectId));
      setFile(null);
      setFileInputKey((value) => value + 1);
      setMessage('资料上传完成，可用于阶段推进。');
      onChanged?.();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '上传失败，请重试');
    } finally {
      setBusy(false);
    }
  }

  async function download(item: ProjectDocument) {
    setError('');
    try {
      await openProjectDocument(projectId, item.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '下载失败');
    }
  }

  return (
    <div className="panel">
      <h2>项目资料</h2>
      <p className="subtle">资料保存在对象存储中；申请阶段推进前，请先上传对应材料。</p>
      {error && <Feedback tone="error">{error}</Feedback>}
      {message && <Feedback tone="success">{message}</Feedback>}
      {!readOnly && (
        <form className="inline-form" onSubmit={upload}>
          <label>
            资料用途
            <select
              value={purpose}
              onChange={(event) => setPurpose(event.target.value as DocumentPurpose)}
            >
              {Object.entries(purposes).map(([code, label]) => (
                <option key={code} value={code}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            选择文件
            <input
              key={fileInputKey}
              type="file"
              accept=".pdf,.docx,.pptx,.png,.jpg,.jpeg"
              required
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <button type="submit" className="secondary-button" disabled={busy || !file}>
            {busy ? '上传并校验中…' : '上传资料'}
          </button>
        </form>
      )}
      <div className="document-list">
        {items.length ? (
          items.map((item) => (
            <div key={item.id}>
              <div>
                <strong>{item.originalName}</strong>
                <small>
                  {purposes[item.purposeCode]} · {dateTime(item.completedAt)}
                </small>
              </div>
              <button type="button" className="text-button" onClick={() => void download(item)}>
                下载
              </button>
            </div>
          ))
        ) : (
          <div className="empty-state">暂无项目资料</div>
        )}
      </div>
    </div>
  );
}
