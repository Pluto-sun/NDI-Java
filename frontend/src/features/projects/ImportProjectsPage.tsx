import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { projectsApi } from './api';

export function ImportProjectsPage() {
  return (
    <ImportWizardPage
      config={{
        api: projectsApi,
        title: '项目',
        backTo: '/projects/new',
        amountFields: ['项目总金额（万）'],
        noDuplicateMessage: '未发现相似项目',
        uploadHint: '项目负责人可用分号分隔。',
        previewHint:
          '同省同地市内比对项目名称。文件内完全同名仅保留首条；库内同名及高度相似须差异说明，其他相似项目需确认。',
      }}
    />
  );
}
