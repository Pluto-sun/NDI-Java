import { useEffect, useState, type FormEvent } from 'react';
import { projectsApi } from './api';
import type { ProjectDetail, ProjectUser, ReferenceData } from './types';
import { Feedback } from '../../shared/ui/Feedback';
import { parseSupportExperts } from './supportExperts';

export function ProjectProfileForm({
  project,
  leaders,
  reference,
  onSaved,
}: {
  project: ProjectDetail;
  leaders: ProjectUser[];
  reference: ReferenceData;
  onSaved: (project: ProjectDetail) => void;
}) {
  const [open, setOpen] = useState(false);
  const [customerName, setCustomerName] = useState(project.customerName);
  const [industryCode, setIndustryCode] = useState(project.industryCode);
  const [customerNeed, setCustomerNeed] = useState(project.customerNeed);
  const [provinceLeaderUserId, setProvinceLeaderUserId] = useState(project.provinceLeaderUserId);
  const [cityAccountManagerName, setCityAccountManagerName] = useState(
    project.cityAccountManagerName,
  );
  const [supportExpertsText, setSupportExpertsText] = useState(
    (project.productSupportExpertNames ?? []).join('\n'),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    setCustomerName(project.customerName);
    setIndustryCode(project.industryCode);
    setCustomerNeed(project.customerNeed);
    setProvinceLeaderUserId(project.provinceLeaderUserId);
    setCityAccountManagerName(project.cityAccountManagerName);
    setSupportExpertsText((project.productSupportExpertNames ?? []).join('\n'));
  }, [project]);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const updated = await projectsApi.updateProfile(project.id, {
        expectedVersion: project.versionNo,
        customerName: customerName.trim(),
        industryCode,
        customerNeed: customerNeed.trim(),
        provinceLeaderUserId,
        cityAccountManagerName: cityAccountManagerName.trim(),
        productSupportExpertNames: parseSupportExperts(supportExpertsText),
      });
      onSaved(updated);
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败，请刷新项目后重试');
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="panel">
      <div className="panel-top">
        <h2>普通信息维护</h2>
        <button type="button" className="secondary-button" onClick={() => setOpen(!open)}>
          {open ? '收起' : '编辑'}
        </button>
      </div>
      <p className="subtle">名称、金额、回退及终止需另走重大变更申请。普通信息保存后即时生效。</p>
      {open && (
        <form className="inline-form" onSubmit={save}>
          {error && <Feedback tone="error">{error}</Feedback>}
          <label>
            客户名称
            <input
              required
              maxLength={200}
              value={customerName}
              onChange={(event) => setCustomerName(event.target.value)}
            />
          </label>
          <label>
            所属行业
            <select
              required
              value={industryCode}
              onChange={(event) => setIndustryCode(event.target.value)}
            >
              {reference.industries.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            客户需求
            <textarea
              required
              rows={4}
              maxLength={10000}
              value={customerNeed}
              onChange={(event) => setCustomerNeed(event.target.value)}
            />
          </label>
          <label>
            省分负责人
            <select
              required
              value={provinceLeaderUserId}
              onChange={(event) => setProvinceLeaderUserId(event.target.value)}
            >
              {leaders.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.displayName}
                </option>
              ))}
            </select>
          </label>
          <label>
            地市客户经理
            <input
              required
              maxLength={100}
              value={cityAccountManagerName}
              onChange={(event) => setCityAccountManagerName(event.target.value)}
            />
          </label>
          <label>
            产品单位支撑专家（选填）
            <textarea
              rows={2}
              value={supportExpertsText}
              onChange={(event) => setSupportExpertsText(event.target.value)}
              placeholder="多位专家请换行或用逗号分隔；不自动获得项目权限"
            />
          </label>
          <button className="primary-button" type="submit" disabled={busy || !leaders.length}>
            {busy ? '保存中…' : '保存普通信息'}
          </button>
        </form>
      )}
    </div>
  );
}
