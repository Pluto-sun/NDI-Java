import './ProjectStageTabs.css';

export const PROJECT_STAGE_TABS = [
  { code: '', label: '全部' },
  { code: 'SOLUTION_EXCHANGE', label: '方案交流' },
  { code: 'BIDDING', label: '招投标' },
  { code: 'SIGNED', label: '签约' },
  { code: 'COLLECTION', label: '计收' },
] as const;

/** 即时应用的阶段筛选，列表和驾驶舱共享稳定代码及交互。 */
export function ProjectStageTabs({
  value,
  onChange,
}: {
  value: string;
  onChange: (stage: string) => void;
}) {
  return (
    <div className="project-stage-tabs" role="group" aria-label="项目阶段快捷筛选">
      {PROJECT_STAGE_TABS.map((item) => (
        <button
          key={item.code}
          type="button"
          aria-pressed={value === item.code}
          onClick={() => onChange(item.code)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
