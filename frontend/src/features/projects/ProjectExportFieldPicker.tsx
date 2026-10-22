import { useEffect, useState } from 'react';
import { projectsApi } from './api';
import type { ExportFieldOption } from './types';
import { Feedback } from '../../shared/ui/Feedback';

/** 字段清单由服务端白名单提供，避免前后端分别维护列代码。 */
export function ProjectExportFieldPicker({
  selected,
  onChange,
}: {
  selected: string[] | null;
  onChange: (fields: string[] | null) => void;
}) {
  const [options, setOptions] = useState<ExportFieldOption[] | null>(null);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    onChange(null);
    setOptions(null);
    setError('');
    projectsApi
      .exportFields()
      .then((result) => {
        if (active) {
          setOptions(result);
          onChange(result.filter((field) => field.defaultSelected).map((field) => field.code));
        }
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '导出字段加载失败');
      });
    return () => {
      active = false;
    };
  }, [revision, onChange]);
  return (
    <details className="panel export-field-picker" open={error ? true : undefined}>
      <summary>导出字段设置{selected ? `（已选 ${selected.length} 项）` : ''}</summary>
      {error ? (
        <Feedback tone="error">
          {error}{' '}
          <button
            type="button"
            className="text-button"
            onClick={() => setRevision((value) => value + 1)}
          >
            重试加载导出字段
          </button>
        </Feedback>
      ) : !options ? (
        <Feedback>正在加载导出字段…</Feedback>
      ) : (
        <>
          <p className="subtle">
            默认使用既定摘要列；按勾选顺序导出，取消后重新勾选可调整顺序。修改字段后需要重新生成文件。
          </p>
          <fieldset className="export-field-options" aria-label="导出字段选择">
            <legend>选择导出字段</legend>
            {options.map((field) => (
              <label key={field.code} className="checkbox-label">
                <input
                  type="checkbox"
                  checked={selected?.includes(field.code) ?? false}
                  onChange={(event) =>
                    onChange(
                      event.target.checked
                        ? [...(selected ?? []), field.code]
                        : (selected ?? []).filter((code) => code !== field.code),
                    )
                  }
                />
                {field.label}
              </label>
            ))}
          </fieldset>
          <div className="action-row">
            <button
              type="button"
              className="text-button"
              onClick={() =>
                onChange(
                  options.filter((field) => field.defaultSelected).map((field) => field.code),
                )
              }
            >
              恢复默认导出字段
            </button>
            <button
              type="button"
              className="text-button"
              onClick={() => onChange(options.map((field) => field.code))}
            >
              全选导出字段
            </button>
            <button type="button" className="text-button" onClick={() => onChange([])}>
              清空导出字段
            </button>
          </div>
          {selected?.length === 0 && <Feedback tone="error">请至少选择一个导出字段。</Feedback>}
          {!!selected?.length && (
            <p className="subtle">
              导出顺序：
              {selected
                .map((code) => options.find((field) => field.code === code)?.label ?? code)
                .join('、')}
            </p>
          )}
        </>
      )}
    </details>
  );
}
