export function LiveAgentImportTabs({
  monthly,
  disabled,
  onChange,
}: {
  monthly: boolean;
  disabled: boolean;
  onChange: (monthly: boolean) => void;
}) {
  return (
    <div className="live-agent-import-tabs" role="group" aria-label="导入内容">
      <button
        type="button"
        className="text-button"
        aria-pressed={!monthly}
        disabled={disabled}
        onClick={() => {
          if (monthly) onChange(false);
        }}
      >
        Excel 已上线智能体指标追踪导入
      </button>
      <button
        type="button"
        className="text-button"
        aria-pressed={monthly}
        disabled={disabled}
        onClick={() => {
          if (!monthly) onChange(true);
        }}
      >
        月度 Token 导入
      </button>
    </div>
  );
}
