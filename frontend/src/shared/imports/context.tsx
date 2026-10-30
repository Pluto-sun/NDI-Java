import { createContext, useContext } from 'react';
import type { ImportBatch, ImportConfiguration, ImportTask } from './types';

export interface ImportWizardApi {
  importTask: () => Promise<ImportTask | null>;
  uploadImportTask: (file: File, version?: number, id?: string) => Promise<ImportTask>;
  saveImportTask: (
    id: string,
    version: number,
    step: number,
    config: ImportConfiguration,
    selected: number[] | null,
  ) => Promise<ImportTask>;
  discardImportTask: (version: number, id: string) => Promise<unknown>;
  confirmImport: (id: string, selected: number[]) => Promise<ImportBatch>;
  importTemplate: () => Promise<Blob>;
  importBatch: (id: string) => Promise<ImportBatch>;
  reviseImportRow: (
    id: string,
    row: number,
    revision: number,
    changes: Record<string, string>,
  ) => Promise<ImportBatch>;
  decideImportName?: (
    id: string,
    row: number,
    revision: number,
    reason: string,
    fingerprint: string,
  ) => Promise<ImportBatch>;
  failedImportRows?: (id: string) => Promise<Blob>;
}
export interface ImportWizardConfig {
  api: ImportWizardApi;
  title: string;
  backTo: string;
  amountFields: string[];
  amountUnit?: string;
  rowTitleFields?: string[];
  rowLocationFields?: string[];
  noDuplicateMessage: string;
  uploadHint: string;
  previewHint: string;
}
const Context = createContext<ImportWizardConfig | null>(null);
export const ImportWizardProvider = Context.Provider;
export function useImportWizard() {
  const value = useContext(Context);
  if (!value) throw new Error('导入向导未配置');
  return value;
}
