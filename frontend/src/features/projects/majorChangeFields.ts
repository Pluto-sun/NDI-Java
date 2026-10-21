import type { ProjectChangeField, ReferenceData } from './types';

export const changeFieldLabels: Record<ProjectChangeField, string> = {
  PROJECT_NAME: '项目名称',
  TOTAL_AMOUNT: '项目总金额',
  NETWORK_AMOUNT: '网络化金额',
  GEWU_AMOUNT: '格物金额',
  BIG_DATA_AMOUNT: '大数据金额',
  WANGWU_AMOUNT: '万悟金额',
  STAGE: '项目阶段',
};

export function changeValue(
  field: ProjectChangeField,
  value: string | null,
  reference: ReferenceData,
) {
  if (value === null) return '未选择';
  if (field === 'STAGE')
    return reference.stages.find((stage) => stage.code === value)?.label ?? value;
  if (field === 'PROJECT_NAME') return value;
  return `${value} 万元`;
}
