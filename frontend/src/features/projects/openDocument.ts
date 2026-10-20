import { projectsApi } from './api';

export async function openProjectDocument(projectId: string, documentId: string): Promise<void> {
  // 在用户点击时先创建窗口，避免预签名地址请求完成后被浏览器拦截。
  const preview = window.open('', '_blank');
  if (preview) preview.opener = null;
  try {
    const { downloadUrl } = await projectsApi.downloadUrl(projectId, documentId);
    if (preview) preview.location.href = downloadUrl;
    else window.location.assign(downloadUrl);
  } catch (cause) {
    preview?.close();
    throw cause;
  }
}
