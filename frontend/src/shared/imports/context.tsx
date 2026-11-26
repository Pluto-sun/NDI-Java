import { createContext, useContext } from 'react';
import type { ImportBatch, ImportConfiguration, ImportTask } from './types';

export interface ImportWizardApi {
  importTask: () => Promise<ImportTask | null>;
  uploadImportTask: (
    file: File,
    version?: number,
    id?: string,
    signal?: AbortSignal,
  ) => Promise<ImportTask>;
  saveImportTask: (
    id: string,
    version: number,
    step: number,
    config: ImportConfiguration,
    selected: number[] | null,
    signal?: AbortSignal,
  ) => Promise<ImportTask>;
  discardImportTask: (version: number, id: string) => Promise<unknown>;
  confirmImport: (id: string, selected: number[], signal?: AbortSignal) => Promise<ImportBatch>;
  importTemplate: () => Promise<Blob>;
  importBatch: (id: string) => Promise<ImportBatch>;
  reviseImportRow: (
    id: string,
    row: number,
    revision: number,
    changes: Record<string, string>,
    signal?: AbortSignal,
  ) => Promise<ImportBatch>;
  decideImportName?: (
    id: string,
    row: number,
    revision: number,
    reason: string,
    fingerprint: string,
    signal?: AbortSignal,
  ) => Promise<ImportBatch>;
  originalFailedImportRows?: (id: string) => Promise<Blob>;
  failedImportRows?: (id: string) => Promise<Blob>;
}
export interface ImportWizardConfig {
  manualSelectionAfterRepair?: boolean;
  api: ImportWizardApi;
  title: string;
  backTo: string;
  entryHint?: string;
  hiddenRowFields?: string[];
  displayCell?: (field: string, value: string) => string;
  formatAmount?: (value: string) => string;
  amountFields: string[];
  requireAmountMapping?: boolean;
  amountUnit?: string;
  emptyValueLabel?: string;
  fieldLabel?: (field: string) => string;
  multipleFieldOptions?: (field: string) => { code: string; label: string }[] | undefined;
  fieldOptions?: (field: string, values: Record<string, string>) => string[] | undefined;
  changeRowField?: (
    values: Record<string, string>,
    field: string,
    value: string,
  ) => Record<string, string>;
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
