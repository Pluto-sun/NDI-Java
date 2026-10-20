import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { projectsApi } from './api';
import type { ProjectUser, ReferenceData } from './types';
import { Feedback } from '../../shared/ui/Feedback';
import { amountToCents } from '../../shared/ui/format';

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
  const [initialProgress, setInitialProgress] = useState('');
  const [nextAction, setNextAction] = useState('');
  const [categories, setCategories] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
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
    setBusy(true);
    setError('');
    try {
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
        categoryAmounts,
        initialProgress,
        nextAction,
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
              onChange={(event) => setCityId(event.target.value)}
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
              onChange={(event) => setProjectName(event.target.value)}
            />
          </label>
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
        <div className="form-actions">
          <Link className="secondary-button" to="/projects">
            取消
          </Link>
          <button className="primary-button" disabled={busy} type="submit">
            {busy ? '正在创建…' : '创建项目'}
          </button>
        </div>
      </form>
    </section>
  );
}
