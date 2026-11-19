export const MAX_EXCEL_ROWS = 2000;
const MAX_EXCEL_BYTES = 20 * 1024 * 1024;

export function validateExcelUpload(file: File) {
  if (!file.size || file.size > MAX_EXCEL_BYTES) {
    throw new Error('Excel 文件须为 1 字节至 20 MB');
  }
}
