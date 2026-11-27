/** 对象存储响应指定附件文件名，链接在当前页面触发浏览器下载。 */
export function downloadAttachment(url: string, fileName = '') {
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
}

export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  downloadAttachment(url, fileName);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
