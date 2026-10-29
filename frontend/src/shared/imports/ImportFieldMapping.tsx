import { allowedMappingSource } from './importWizard';
import { Select } from '../ui/Select';
import type { ImportConfiguration, ImportTask } from './types';

export function ImportFieldMapping({
  task,
  configuration,
  onChange,
  disabled,
}: {
  task: ImportTask;
  configuration: ImportConfiguration;
  onChange: (value: ImportConfiguration) => void;
  disabled: boolean;
}) {
  const headers =
    task.inspection.sheets
      .find((sheet) => sheet.name === configuration.sheetName)
      ?.headerCandidates.find((row) => row.rowNo === configuration.headerRowNo)?.headers ?? [];
  return (
    <section aria-label="字段映射设置">
      <h2 tabIndex={-1}>第二步：字段映射</h2>
      <p className="subtle">
        已根据表头自动匹配，请逐项核对。必填列完成映射后才可开始预检；行内空值仍可在预检中修正。
      </p>
      <div
        className="import-mapping-scroll table-scroll"
        tabIndex={0}
        aria-label="字段映射表格，可滚动"
      >
        <table>
          <thead>
            <tr>
              <th>系统字段</th>
              <th>填写要求</th>
              <th>Excel 表头</th>
              <th>填写说明</th>
            </tr>
          </thead>
          <tbody>
            {task.fieldRules.map((rule) => (
              <tr key={rule.name}>
                <th>{rule.name}</th>
                <td>
                  <span
                    className={`import-requirement requirement-${rule.requirement.toLowerCase()}`}
                  >
                    {rule.requirement === 'REQUIRED'
                      ? '必填'
                      : rule.requirement === 'CONDITIONAL'
                        ? '条件必填'
                        : '选填'}
                  </span>
                </td>
                <td>
                  <label className="sr-only" htmlFor={`map-${rule.name}`}>
                    映射：{rule.name}
                  </label>
                  <Select
                    id={`map-${rule.name}`}
                    value={configuration.fieldMapping[rule.name] ?? ''}
                    disabled={disabled}
                    onChange={(event) =>
                      onChange({
                        ...configuration,
                        fieldMapping: {
                          ...configuration.fieldMapping,
                          [rule.name]: event.target.value,
                        },
                      })
                    }
                  >
                    <option value="">不映射</option>
                    {rule.name === '三化类别' &&
                      headers.some((h) => /^三化类别[一二三四]$/.test(h)) && (
                        <option value="__CATEGORY_SLOTS__">合并三化类别一至四</option>
                      )}
                    {headers.map((header) => (
                      <option
                        key={header}
                        value={header}
                        disabled={
                          !allowedMappingSource(rule.name, header) ||
                          Object.entries(configuration.fieldMapping).some(
                            ([field, value]) => field !== rule.name && value === header,
                          )
                        }
                      >
                        {header}
                      </option>
                    ))}
                  </Select>
                </td>
                <td className="subtle">{rule.hint}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
