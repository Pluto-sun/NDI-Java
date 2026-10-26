export interface ImportBatch {
  batchId: string;
  status: string;
  totalRows: number;
  validRows: number;
  failedRows: number;
  skippedRows: number;
  importedRows: number;
  confirmedAt: string | null;
  rows: {
    rowNumber: number;
    status: string;
    errorsJson: string;
    projectId?: string | null;
    recordId?: string | null;
    sourceCellsJson: string | null;
    revisionNo: number;
    nameReview: ImportNameReview | null;
  }[];
}
export interface ImportWorkbookInspection {
  fileName: string;
  fields: string[];
  sheets: {
    name: string;
    headerCandidates: { rowNo: number; headers: string[] }[];
  }[];
}
export interface ImportNameReview {
  provinceId: string;
  cityId: string;
  level: 'NONE' | 'WEAK' | 'STRONG';
  score: number;
  fingerprint: string;
  evidence: string[];
  matches: {
    projectId: string | null;
    projectCode: string | null;
    projectName: string;
    sourceRowNumber: number | null;
    score: number;
  }[];
  confirmed: boolean;
  reason: string | null;
}
export interface ImportConfiguration {
  sheetName: string | null;
  headerRowNo: number | null;
  fieldMapping: Record<string, string>;
}
export interface ImportTask {
  id: string;
  version: number;
  step: 1 | 2 | 3;
  fileName: string;
  inspection: ImportWorkbookInspection;
  configuration: ImportConfiguration;
  selectedRows: number[] | null;
  batch: ImportBatch | null;
  updatedAt: string;
  fieldRules: {
    name: string;
    requirement: 'REQUIRED' | 'OPTIONAL' | 'CONDITIONAL';
    hint: string;
  }[];
}
