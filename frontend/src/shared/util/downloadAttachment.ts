/** 对象存储响应指定附件文件名，链接在当前页面触发浏览器下载。 */
export function downloadAttachment(url: string) {
  const link = document.createElement('a');
  link.href = url;
  link.download = '';
  document.body.append(link);
  link.click();
  link.remove();
}
