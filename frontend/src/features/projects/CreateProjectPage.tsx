import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { projectsApi } from './api';
import type {
  ProjectDraft,
  ProjectDraftData,
  ProjectDuplicateCheck,
  ProjectUser,
  ReferenceData,
} from './types';
import type { Page } from '../../shared/api/http';
import { Feedback } from '../../shared/ui/Feedback';
import { amountToCents } from '../../shared/ui/format';
import { dateTime } from '../../shared/ui/format';
import { parseSupportExperts } from './supportExperts';

export function CreateProjectPage({
  me,
  reference,
}: {
  me: ProjectUser;
  reference: ReferenceData;
}) {
  const navigate = useNavigate();
  const [cityId, setCityId] = useState(me.cityId ?? '');
  const [leaders, setLeaders] = useState<ProjectUser[]>([]);
  const [leaderId, setLeaderId] = useState('');
  const [projectName, setProjectName] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [industryCode, setIndustryCode] = useState('');
  const [customerNeed, setCustomerNeed] = useState('');
  const [totalAmount, setTotalAmount] = useState('');
  const [cityAccountManagerName, setCityAccountManagerName] = useState('');
  const [supportExpertsText, setSupportExpertsText] = useState('');
  const [signingSubjectCode, setSigningSubjectCode] = useState<'PROVINCE_SIGN' | 'DS_SIGN'>(
    'PROVINCE_SIGN',
  );
  const [initialProgress, setInitialProgress] = useState('');
  const [nextAction, setNextAction] = useState('');
  const [categories, setCategories] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [draftBusy, setDraftBusy] = useState(false);
  const [draftId, setDraftId] = useState<string | undefined>();
  const [drafts, setDrafts] = useState<Page<ProjectDraft> | null>(null);
  const [draftPage, setDraftPage] = useState(0);
  const [message, setMessage] = useState('');
  const [candidate, setCandidate] = useState<ProjectDuplicateCheck | null>(null);
  const [checking, setChecking] = useState(false);
  const [confirmNew, setConfirmNew] = useState(false);
  const [duplicateReason, setDuplicateReason] = useState('');
  const checkRevision = useRef(0);
  const provinceId = me.provinceId;
  const isCity =
    me.roleCodes.includes('CITY_REPORTER') &&
    !me.roleCodes.some((role) => role.startsWith('PROVINCE_'));
  const cities = reference.cities.filter(
    (item) => item.provinceId === provinceId && (!isCity || item.id === me.cityId),
  );

  useEffect(() => {
    if (!provinceId) return;
    projectsApi
      .provinceLeaders(provinceId)
      .then(setLeaders)
      .catch((cause) => setError(cause instanceof Error ? cause.message : '负责人目录加载失败'));
  }, [provinceId]);

  useEffect(() => {
    let active = true;
    projectsApi
      .drafts(draftPage)
      .then((result) => {
        if (active) setDrafts(result);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : '草稿列表加载失败');
      });
    return () => {
      active = false;
    };
  }, [draftPage, me.id]);

  function formData(): ProjectDraftData {
    return {
      cityId,
      leaderId,
      projectName,
      customerName,
      industryCode,
      customerNeed,
      totalAmount,
      cityAccountManagerName,
      productSupportExpertNames: supportExpertsText.split(/\n/).filter(Boolean),
      signingSubjectCode,
      initialProgress,
      nextAction,
      categories,
      confirmNew,
      duplicateReason,
    };
  }

  async function checkDuplicate() {
    if (!cityId || projectName.trim().length < 2) return null;
    const revision = ++checkRevision.current;
    setChecking(true);
    try {
      const result = await projectsApi.duplicateCheck(cityId, projectName.trim());
      if (revision === checkRevision.current) setCandidate(result);
      return result;
    } catch (cause) {
      if (revision === checkRevision.current)
        setError(cause instanceof Error ? cause.message : '重名检查失败');
      return null;
    } finally {
      if (revision === checkRevision.current) setChecking(false);
    }
  }

  async function saveDraft() {
    setDraftBusy(true);
    setError('');
    setMessage('');
    try {
      const saved = await projectsApi.saveDraft(formData(), draftId);
      setDraftId(saved.id);
      setDraftPage(0);
      setDrafts(await projectsApi.drafts(0));
      setMessage('草稿已保存，不会进入项目统计。');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '草稿保存失败');
    } finally {
      setDraftBusy(false);
    }
  }

  function restoreDraft(draft: ProjectDraft) {
    if (!window.confirm(`载入草稿“${draft.title}”？当前未保存内容会被替换。`)) return;
    const data = draft.data;
    setDraftId(draft.id);
    setCityId(data.cityId ?? '');
    setLeaderId(data.leaderId ?? '');
    setProjectName(data.projectName ?? '');
    setCustomerName(data.customerName ?? '');
    setIndustryCode(data.industryCode ?? '');
    setCustomerNeed(data.customerNeed ?? '');
    setTotalAmount(data.totalAmount ?? '');
    setCityAccountManagerName(data.cityAccountManagerName ?? '');
    setSupportExpertsText((data.productSupportExpertNames ?? []).join('\n'));
    setSigningSubjectCode(data.signingSubjectCode === 'DS_SIGN' ? 'DS_SIGN' : 'PROVINCE_SIGN');
    setInitialProgress(data.initialProgress ?? '');
    setNextAction(data.nextAction ?? '');
    setCategories(
      data.categories && typeof data.categories === 'object' && !Array.isArray(data.categories)
        ? data.categories
        : {},
    );
    setConfirmNew(Boolean(data.confirmNew));
    setDuplicateReason(data.duplicateReason ?? '');
    setCandidate(null);
    checkRevision.current += 1;
    setChecking(false);
    setMessage(`已载入草稿“${draft.title}”。`);
    setError('');
  }

  async function deleteDraft(draft: ProjectDraft) {
    if (!window.confirm(`删除草稿“${draft.title}”？此操作不能恢复。`)) return;
    setDraftBusy(true);
    setError('');
    try {
      await projectsApi.deleteDraft(draft.id);
      if (draftId === draft.id) setDraftId(undefined);
      setDrafts(await projectsApi.drafts(draftPage));
      setMessage('草稿已删除。');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '草稿删除失败');
    } finally {
      setDraftBusy(false);
    }
  }

  const categoryAmounts = useMemo(
    () =>
      Object.entries(categories).map(([categoryCode, expectedAmount]) => ({
        categoryCode,
        expectedAmount,
      })),
    [categories],
  );
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (projectName.trim().length < 2) {
      setError('项目名称至少需要 2 个字符。');
      return;
    }
    if (!provinceId || !cityId || !leaderId) {
      setError('请选择地市和省分负责人。');
      return;
    }
    const totalCents = amountToCents(totalAmount);
    if (totalCents === null) {
      setError('请填写不小于 0 且最多两位小数的项目总金额。');
      return;
    }
    if (
      categoryAmounts.length === 0 ||
      categoryAmounts.some((item) => amountToCents(item.expectedAmount) === null)
    ) {
      setError('至少选择一项三化类别，并填写非负金额。');
      return;
    }
    if (
      categoryAmounts.reduce((sum, item) => sum + amountToCents(item.expectedAmount)!, 0n) >
      totalCents
    ) {
      setError('三化分类金额合计不能超过项目总金额。');
      return;
    }
    const duplicate = await checkDuplicate();
    if (!duplicate) return;
    if (duplicate.status === 'STRONG_DUPLICATE') {
      setError('同地市已有相同名称的项目，请打开现有项目追加进展。');
      return;
    }
    if (duplicate.status === 'DUPLICATE_CANDIDATE' && (!confirmNew || !duplicateReason.trim())) {
      setError('发现相似项目。若仍需另建，请勾选确认并填写原因。');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const productSupportExpertNames = parseSupportExperts(supportExpertsText);
      const created = await projectsApi.create({
        provinceId,
        cityId,
        projectName,
        customerName,
        industryCode,
        customerNeed,
        totalAmount,
        provinceLeaderUserId: leaderId,
        cityAccountManagerName,
        productSupportExpertNames,
        signingSubjectCode,
        categoryAmounts,
        initialProgress,
        nextAction,
        draftId,
        confirmNew,
        duplicateReason: duplicateReason.trim(),
      });
      navigate(`/projects/${created.id}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '创建失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">NEW PROJECT</span>
          <h1>新建项目</h1>
          <p>创建后立即进入“方案交流”；项目名称与金额等重大变更需另行申请。</p>
        </div>
        <Link className="text-button" to="/projects">
          返回列表
        </Link>
      </div>
      <div className="panel draft-panel">
        <div className="panel-top">
          <strong>我的项目草稿</strong>
          <span>{drafts?.totalElements ?? 0} 条</span>
        </div>
        <p className="subtle">草稿仅本人可见；载入草稿前会提示替换当前表单。</p>
        {drafts?.items.map((draft) => (
          <div className="draft-item" key={draft.id}>
            <div>
              <strong>{draft.title}</strong>
              <small>{dateTime(draft.updatedAt)}</small>
            </div>
            <div className="action-row">
              <button
                type="button"
                className="secondary-button"
                disabled={draftBusy}
                onClick={() => restoreDraft(draft)}
              >
                继续填写
              </button>
              <button
                type="button"
                className="text-button"
                disabled={draftBusy}
                onClick={() => void deleteDraft(draft)}
              >
                删除
              </button>
            </div>
          </div>
        ))}
        {!drafts?.items.length && (
          <div className="empty-state">暂无草稿。填写过程中可随时保存。</div>
        )}
        {(drafts?.totalPages ?? 0) > 1 && (
          <div className="pager">
            <span>
              第 {draftPage + 1} / {drafts?.totalPages} 页
            </span>
            <div>
              <button
                type="button"
                className="secondary-button"
                disabled={draftPage === 0}
                onClick={() => setDraftPage(draftPage - 1)}
              >
                上一页
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={draftPage + 1 >= (drafts?.totalPages ?? 0)}
                onClick={() => setDraftPage(draftPage + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        )}
      </div>
      <form className="panel form-panel" onSubmit={submit}>
        <h2>基本信息</h2>
        <div className="form-grid">
          <label>
            归属省分
            <input
              value={reference.provinces.find((item) => item.id === provinceId)?.name ?? '未设置'}
              disabled
            />
          </label>
          <label>
            归属地市
            <select
              required
              value={cityId}
              onChange={(event) => {
                setCityId(event.target.value);
                setCandidate(null);
                setConfirmNew(false);
                setChecking(false);
                checkRevision.current += 1;
              }}
              disabled={isCity}
            >
              <option value="">请选择</option>
              {cities.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            项目名称
            <input
              required
              maxLength={200}
              value={projectName}
              onChange={(event) => {
                setProjectName(event.target.value);
                setCandidate(null);
                setConfirmNew(false);
                setChecking(false);
                checkRevision.current += 1;
              }}
              onBlur={() => {
                if (projectName.trim().length >= 2) void checkDuplicate();
              }}
            />
          </label>
          <div className="span-two duplicate-check">
            <button
              type="button"
              className="secondary-button"
              disabled={!cityId || projectName.trim().length < 2 || checking}
              onClick={() => void checkDuplicate()}
            >
              {checking ? '检查中…' : '检查同地市重名'}
            </button>
            {candidate?.status === 'NONE' && (
              <p role="status" className="subtle">
                未发现同名或相似项目。
              </p>
            )}
            {candidate && candidate.candidates.length > 0 && (
              <div className="duplicate-results" role="status">
                <strong>
                  {candidate.status === 'STRONG_DUPLICATE'
                    ? '存在完全同名项目，不能重复创建'
                    : '发现相似项目，请先核对'}
                </strong>
                <ul>
                  {candidate.candidates.map((item) => (
                    <li key={item.projectId}>
                      <Link
                        className="inline-link"
                        to={`/projects/${item.projectId}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {item.projectCode} · {item.projectName}
                      </Link>
                      <span>相似度 {item.similarity}%</span>
                    </li>
                  ))}
                </ul>
                {candidate.status === 'DUPLICATE_CANDIDATE' && (
                  <>
                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        checked={confirmNew}
                        onChange={(event) => setConfirmNew(event.target.checked)}
                      />
                      确认仍需另建项目
                    </label>
                    {confirmNew && (
                      <label>
                        另建原因
                        <textarea
                          required
                          maxLength={2000}
                          rows={2}
                          value={duplicateReason}
                          onChange={(event) => setDuplicateReason(event.target.value)}
                        />
                      </label>
                    )}
                  </>
                )}
              </div>
            )}
          </div>
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
            客户所属行业
            <select
              required
              value={industryCode}
              onChange={(event) => setIndustryCode(event.target.value)}
            >
              <option value="">请选择</option>
              {reference.industries.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            项目总金额（万元）
            <input
              required
              type="number"
              min="0"
              step="0.01"
              value={totalAmount}
              onChange={(event) => setTotalAmount(event.target.value)}
            />
          </label>
          <label className="span-two">
            客户需求
            <textarea
              required
              rows={3}
              value={customerNeed}
              onChange={(event) => setCustomerNeed(event.target.value)}
            />
          </label>
        </div>
        <h2>责任与分类</h2>
        <div className="form-grid">
          <label>
            省分负责人
            <select required value={leaderId} onChange={(event) => setLeaderId(event.target.value)}>
              <option value="">请选择</option>
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
            签约主体
            <select
              value={signingSubjectCode}
              onChange={(event) =>
                setSigningSubjectCode(
                  event.target.value === 'DS_SIGN' ? 'DS_SIGN' : 'PROVINCE_SIGN',
                )
              }
            >
              <option value="PROVINCE_SIGN">省分签约</option>
              <option value="DS_SIGN">数科签约</option>
            </select>
          </label>
          <label>
            产品单位支撑专家（选填）
            <textarea
              rows={2}
              value={supportExpertsText}
              onChange={(event) => setSupportExpertsText(event.target.value)}
              placeholder="多位专家请换行或用逗号分隔；仅用于责任记录"
            />
          </label>
        </div>
        <div className="category-grid">
          {reference.categories.map((item) => (
            <div className="category-item" key={item.code}>
              <label className="check-label">
                <input
                  type="checkbox"
                  checked={item.code in categories}
                  onChange={(event) =>
                    setCategories((current) => {
                      const next = { ...current };
                      if (event.target.checked) next[item.code] = '';
                      else delete next[item.code];
                      return next;
                    })
                  }
                />
                {item.label}
              </label>
              {item.code in categories && (
                <label>
                  预计金额（万元）
                  <input
                    required
                    type="number"
                    min="0"
                    step="0.01"
                    value={categories[item.code]}
                    onChange={(event) =>
                      setCategories((current) => ({
                        ...current,
                        [item.code]: event.target.value,
                      }))
                    }
                  />
                </label>
              )}
            </div>
          ))}
        </div>
        <h2>首条进展</h2>
        <div className="form-grid">
          <label className="span-two">
            项目进展
            <textarea
              required
              rows={3}
              value={initialProgress}
              onChange={(event) => setInitialProgress(event.target.value)}
            />
          </label>
          <label className="span-two">
            下一步动作
            <textarea
              required
              rows={3}
              value={nextAction}
              onChange={(event) => setNextAction(event.target.value)}
            />
          </label>
        </div>
        {error && <Feedback tone="error">{error}</Feedback>}
        {message && <Feedback tone="success">{message}</Feedback>}
        <div className="form-actions">
          <Link className="secondary-button" to="/projects">
            取消
          </Link>
          <button
            className="secondary-button"
            disabled={draftBusy || busy}
            type="button"
            onClick={() => void saveDraft()}
          >
            {draftBusy ? '保存中…' : draftId ? '更新草稿' : '保存草稿'}
          </button>
          <button className="primary-button" disabled={busy} type="submit">
            {busy ? '正在创建…' : '创建项目'}
          </button>
        </div>
      </form>
    </section>
  );
}
