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
  const [constructionContentRaw, setConstructionContentRaw] = useState(
    project.constructionContentRaw,
  );
  const [productPromotionContent, setProductPromotionContent] = useState(
    project.productPromotionContent ?? '',
  );
  const [remark, setRemark] = useState(project.remark ?? '');
  const [provinceLeaderUserId, setProvinceLeaderUserId] = useState(project.provinceLeaderUserId);
  const [cityAccountManagerName, setCityAccountManagerName] = useState(
    project.cityAccountManagerName,
  );
  const [supportExpertsText, setSupportExpertsText] = useState(
    (project.productSupportExpertNames ?? []).join('\n'),
  );
  const [plannedBidMonth, setPlannedBidMonth] = useState(
    project.plannedBidMonth?.slice(0, 7) ?? '',
  );
  const [applicationScenarioCodes, setApplicationScenarioCodes] = useState(
    project.applicationScenarioCodes ?? [],
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    setCustomerName(project.customerName);
    setIndustryCode(project.industryCode);
    setCustomerNeed(project.customerNeed);
    setConstructionContentRaw(project.constructionContentRaw);
    setProductPromotionContent(project.productPromotionContent ?? '');
    setRemark(project.remark ?? '');
    setProvinceLeaderUserId(project.provinceLeaderUserId);
    setCityAccountManagerName(project.cityAccountManagerName);
    setSupportExpertsText((project.productSupportExpertNames ?? []).join('\n'));
    setPlannedBidMonth(project.plannedBidMonth?.slice(0, 7) ?? '');
    setApplicationScenarioCodes(project.applicationScenarioCodes ?? []);
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
        constructionContentRaw,
        productPromotionContent,
        remark,
        provinceLeaderUserId,
        cityAccountManagerName: cityAccountManagerName.trim(),
        productSupportExpertNames: parseSupportExperts(supportExpertsText),
        plannedBidMonth,
        applicationScenarioCodes,
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
          <details className="optional-details">
            <summary>其他项目资料（选填）</summary>
            <label>
              原始项目建设内容
              <textarea
                rows={3}
                maxLength={4000}
                value={constructionContentRaw}
                onChange={(event) => setConstructionContentRaw(event.target.value)}
                placeholder="清空后沿用客户需求"
              />
            </label>
            <label>
              产品推广内容
              <textarea
                rows={3}
                maxLength={4000}
                value={productPromotionContent}
                onChange={(event) => setProductPromotionContent(event.target.value)}
              />
            </label>
            <label>
              备注
              <textarea
                rows={3}
                maxLength={10000}
                value={remark}
                onChange={(event) => setRemark(event.target.value)}
              />
            </label>
          </details>
          <label>
            省分负责人
            <select
              required
              value={provinceLeaderUserId}
              onChange={(event) => setProvinceLeaderUserId(event.target.value)}
            >
              {!leaders.some((item) => item.id === project.provinceLeaderUserId) && (
                <option value={project.provinceLeaderUserId}>
                  {project.provinceLeaderName ?? '现有负责人'}（当前责任信息）
                </option>
              )}
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
          <label>
            拟招标月份（选填）
            <input
              type="month"
              value={plannedBidMonth}
              onChange={(event) => setPlannedBidMonth(event.target.value)}
            />
          </label>
          <fieldset>
            <legend>应用场景（可多选）</legend>
            {reference.applicationScenarios.map((scenario) => (
              <label className="check-label" key={scenario.code}>
                <input
                  type="checkbox"
                  checked={applicationScenarioCodes.includes(scenario.code)}
                  onChange={(event) =>
                    setApplicationScenarioCodes((current) =>
                      event.target.checked
                        ? [...current, scenario.code]
                        : current.filter((code) => code !== scenario.code),
                    )
                  }
                />
                {scenario.label}
              </label>
            ))}
          </fieldset>
          <button className="primary-button" type="submit" disabled={busy || !leaders.length}>
            {busy ? '保存中…' : '保存普通信息'}
          </button>
        </form>
      )}
    </div>
  );
}
