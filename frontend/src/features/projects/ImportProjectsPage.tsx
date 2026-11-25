import { displayImportedProjectStage } from '../../shared/ui/ProjectStageTabs';
import { ImportWizardPage } from '../../shared/imports/ImportWizardPage';
import { projectsApi } from './api';
import type { ReferenceData } from './types';

const provinceName = (name: string) =>
  name.trim().replace(/(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市)$/, '');

export function ImportProjectsPage({ reference }: { reference: ReferenceData }) {
  const provinces = reference.provinces.map(({ name }) => name);
  const options: Record<string, string[]> = {
    省分: provinces,
    协作省份: provinces,
    客户所属行业: reference.industries.map(({ label }) => label),
    项目阶段: reference.stages.map(({ label }) => label),
    签约主体: ['省分签约', '数科签约'],
  };
  function fieldOptions(field: string, values: Record<string, string>) {
    if (field !== '地市') return options[field] ? ['', ...options[field]] : undefined;
    const province = reference.provinces.find(
      ({ name }) => provinceName(name) === provinceName(values['省分'] ?? ''),
    );
    return [
      '',
      ...reference.cities
        .filter(
          ({ provinceId, legacyMunicipalityCity }) =>
            provinceId === province?.id && !legacyMunicipalityCity,
        )
        .map(({ name }) => name),
    ];
  }

  return (
    <ImportWizardPage
      config={{
        api: projectsApi,
        manualSelectionAfterRepair: true,
        displayCell: (field, value) =>
          field === '项目阶段' ? displayImportedProjectStage(value) : value,
        fieldOptions,
        multipleFieldOptions: (field) =>
          field === '应用场景' ? reference.applicationScenarios : undefined,
        changeRowField: (values, field, value) =>
          field === '省分' ? { ...values, 省分: value, 地市: '' } : { ...values, [field]: value },
        title: '项目',
        backTo: '/projects/new',
        amountFields: ['项目总金额（万）'],
        noDuplicateMessage: '未发现相似项目',
        uploadHint:
          '直辖市的省分填写市名，地市填写区县。项目负责人可用分号分隔；三化类别按预计金额自动识别，有值（含 0）即包含该类别，至少填写一项。',
        previewHint:
          '同省同地市内比对项目名称。文件内完全同名仅保留首条；库内同名及高度相似须差异说明，其他相似项目需确认。',
      }}
    />
  );
}
