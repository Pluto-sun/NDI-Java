import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { projectsApi } from './api';
import type {
  ProjectDetail,
  ProjectMilestone,
  ProjectProgress,
  ProjectUser,
  ReferenceData,
} from './types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount, businessDate, dateTime } from '../../shared/ui/format';
import { ProjectDocuments } from './ProjectDocuments';
import { ProjectRequests } from './ProjectRequests';
import { ProjectProfileForm } from './ProjectProfileForm';
import { ProjectPeoplePanel } from './ProjectPeoplePanel';
import { refreshNotificationCounts } from '../approvals/notificationEvents';
import { ProjectHistory } from './ProjectHistory';
import { ProjectInactivityAuditPanel } from './ProjectInactivityAuditPanel';
import { ProjectOperationAuditPanel } from './ProjectOperationAuditPanel';

export function ProjectDetailPage({
  reference,
  me,
}: {
  reference: ReferenceData;
  me: ProjectUser;
}) {
  const { id } = useParams();
  const location = useLocation();
  const candidateReturnTo = (location.state as { projectListReturnTo?: unknown } | null)
    ?.projectListReturnTo;
  const returnTo =
    typeof candidateReturnTo === 'string' && /^\/projects(?:\?|$)/.test(candidateReturnTo)
      ? candidateReturnTo
      : '/projects';
  const [detail, setDetail] = useState<ProjectDetail | null>(null);
  const [progress, setProgress] = useState<ProjectProgress[]>([]);
  const [milestones, setMilestones] = useState<ProjectMilestone[]>([]);
  const [content, setContent] = useState('');
  const [nextAction, setNextAction] = useState('');
  const [progressDate, setProgressDate] = useState('');
  const [ownerName, setOwnerName] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [documentsVersion, setDocumentsVersion] = useState(0);
  const [correcting, setCorrecting] = useState<ProjectProgress | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [project, progresses, stageHistory] = await Promise.all([
        projectsApi.detail(id),
        projectsApi.progresses(id),
        projectsApi.milestones(id),
      ]);
      setDetail(project);
      setProgress(progresses.items);
      setMilestones(stageHistory.items);
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '项目加载失败');
    } finally {
      setLoading(false);
    }
  }, [id]);
  useEffect(() => {
    void load();
  }, [load]);

  async function append(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!id) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      await projectsApi.appendProgress(id, {
        progressContent: content.trim(),
        nextAction: nextAction.trim(),
        correctsProgressId: correcting?.id,
        progressDate: progressDate || undefined,
        ownerName: ownerName.trim() || undefined,
        dueDate: dueDate || undefined,
      });
      setContent('');
      setNextAction('');
      setProgressDate('');
      setOwnerName('');
      setDueDate('');
      setCorrecting(null);
      await load();
      setNotice('项目进展已保存。');
      refreshNotificationCounts();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '进展保存失败');
    } finally {
      setSaving(false);
    }
  }

  const label = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  const canCollaborate =
    !me.roleCodes.includes('SYSTEM_ADMIN') &&
    (me.roleCodes.includes('INTERFACE_REVIEWER')
      ? !!detail && me.reviewerProvinceIds.includes(detail.provinceId)
      : me.provinceId === detail?.provinceId) &&
    (me.roleCodes.some((role) =>
      [
        'PROVINCE_REPORTER',
        'PROVINCE_DIGITAL_LEADER',
        'PROVINCE_INTELLIGENT_LEADER',
        'INTERFACE_REVIEWER',
      ].includes(role),
    ) ||
      (me.roleCodes.includes('CITY_REPORTER') && me.cityId === detail?.cityId));
  const canProgress = canCollaborate && detail?.currentStageCode !== 'TERMINATED';
  return (
    <section>
      <div className="page-heading">
        <div>
          <Link className="back-link" to={returnTo}>
            ← 返回项目列表
          </Link>
          <h1>{detail?.projectName ?? '项目详情'}</h1>
          <p>{detail?.projectCode ?? '主档、进展和里程碑'}</p>
        </div>
        {detail && (
          <span className={`stage stage-${detail.currentStageCode.toLowerCase()}`}>
            {label(detail.currentStageCode)}
          </span>
        )}
      </div>
      {error && <Feedback tone="error">{error}</Feedback>}
      {notice && <Feedback tone="success">{notice}</Feedback>}
      {loading ? (
        <Loading />
      ) : (
        detail && (
          <>
            <ProjectPeoplePanel project={detail} editable={!!canProgress} onSaved={setDetail} />
            <div className="detail-grid">
              <div className="panel">
                <h2>项目主档</h2>
                <dl className="facts">
                  <div>
                    <dt>客户名称</dt>
                    <dd>{detail.customerName}</dd>
                  </div>
                  <div>
                    <dt>项目日期（Asia/Shanghai）</dt>
                    <dd>
                      {detail.opportunityDate
                        ? dateTime(detail.opportunityDate, 'Asia/Shanghai')
                        : '旧日期待核对，未纳入日期范围查询'}
                    </dd>
                  </div>
                  <div>
                    <dt>所属行业</dt>
                    <dd>
                      {reference.industries.find((item) => item.code === detail.industryCode)
                        ?.label ?? detail.industryCode}
                    </dd>
                  </div>
                  <div>
                    <dt>归属地市</dt>
                    <dd>
                      {reference.cities.find((item) => item.id === detail.cityId)?.name ?? '—'}
                    </dd>
                  </div>
                  <div>
                    <dt>项目总金额</dt>
                    <dd>{amount(detail.totalAmount)} 万元</dd>
                  </div>
                  <div>
                    <dt>项目所属月份</dt>
                    <dd>{detail.attributionMonth?.slice(0, 7) ?? '—'}</dd>
                  </div>
                  <div>
                    <dt>拟招标月份</dt>
                    <dd>{detail.plannedBidMonth?.slice(0, 7) ?? '—'}</dd>
                  </div>
                  <div>
                    <dt>应用场景</dt>
                    <dd>
                      {detail.applicationScenarioCodes
                        ?.map(
                          (code) =>
                            reference.applicationScenarios.find((item) => item.code === code)
                              ?.label ?? code,
                        )
                        .join('、') || '—'}
                    </dd>
                  </div>
                  <div>
                    <dt>签约主体</dt>
                    <dd>{detail.signingSubjectCode === 'DS_SIGN' ? '数科签约' : '省分签约'}</dd>
                  </div>
                  <div>
                    <dt>合同编号</dt>
                    <dd>{detail.contractNo ?? '—'}</dd>
                  </div>
                  <div>
                    <dt>签约金额</dt>
                    <dd>
                      {detail.signedAmount == null ? '—' : `${amount(detail.signedAmount)} 万元`}
                    </dd>
                  </div>
                </dl>
                <div className="content-block">
                  <strong>客户需求</strong>
                  <p>{detail.customerNeed}</p>
                </div>
                {detail.constructionContentRaw !== detail.customerNeed && (
                  <div className="content-block">
                    <strong>原始项目建设内容</strong>
                    <p>{detail.constructionContentRaw}</p>
                  </div>
                )}
                {detail.productPromotionContent && (
                  <div className="content-block">
                    <strong>产品推广内容</strong>
                    <p>{detail.productPromotionContent}</p>
                  </div>
                )}
                {detail.remark && (
                  <div className="content-block">
                    <strong>备注</strong>
                    <p>{detail.remark}</p>
                  </div>
                )}
              </div>
              <div className="panel">
                <h2>三化分类金额</h2>
                <div className="amount-list">
                  {detail.categoryAmounts.map((item) => (
                    <div key={item.categoryCode}>
                      <span>
                        {reference.categories.find(
                          (category) => category.code === item.categoryCode,
                        )?.label ?? item.categoryCode}
                      </span>
                      <strong>{amount(item.expectedAmount)} 万元</strong>
                    </div>
                  ))}
                </div>
                <p className="subtle">
                  版本 {detail.versionNo} · 更新于 {dateTime(detail.updatedAt)}
                </p>
              </div>
            </div>
            {canProgress && (
              <ProjectProfileForm
                project={detail}
                reference={reference}
                onSaved={(project) => {
                  setDetail(project);
                  refreshNotificationCounts();
                }}
              />
            )}
            <div className="detail-grid">
              <div className="panel">
                <h2>项目进展</h2>
                {canProgress && (
                  <form className="inline-form" onSubmit={append}>
                    {correcting && (
                      <Feedback>
                        正在更正 {dateTime(correcting.occurredAt)}{' '}
                        的进展。原记录保留，新记录会关联原记录。
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => {
                            setCorrecting(null);
                            setContent('');
                            setNextAction('');
                            setProgressDate('');
                            setOwnerName('');
                            setDueDate('');
                          }}
                        >
                          取消更正
                        </button>
                      </Feedback>
                    )}
                    <label>
                      进展内容
                      <textarea
                        required
                        rows={3}
                        value={content}
                        onChange={(event) => setContent(event.target.value)}
                      />
                    </label>
                    <label>
                      下一步动作
                      <textarea
                        required
                        rows={2}
                        value={nextAction}
                        onChange={(event) => setNextAction(event.target.value)}
                      />
                    </label>
                    <div className="form-grid">
                      <label>
                        进展发生日期（选填）
                        <input
                          type="date"
                          value={progressDate}
                          onChange={(event) => setProgressDate(event.target.value)}
                        />
                      </label>
                      <label>
                        负责人（选填）
                        <input
                          type="text"
                          maxLength={100}
                          value={ownerName}
                          onChange={(event) => setOwnerName(event.target.value)}
                        />
                      </label>
                      <label>
                        计划完成日期（选填）
                        <input
                          type="date"
                          value={dueDate}
                          onChange={(event) => setDueDate(event.target.value)}
                        />
                      </label>
                    </div>
                    <p className="subtle">发生日期不填则按提交当天记录；记录写入时间另行保留。</p>
                    <button type="submit" className="primary-button" disabled={saving}>
                      {saving ? '保存中…' : correcting ? '提交更正' : '追加进展'}
                    </button>
                  </form>
                )}
                {detail.currentStageCode === 'TERMINATED' && (
                  <Feedback>项目已终止，历史进展只读。</Feedback>
                )}
                <div className="timeline">
                  {progress.length ? (
                    progress.map((item) => (
                      <article key={item.id}>
                        <time>进展日期：{item.progressDate ?? businessDate(item.occurredAt)}</time>
                        <p>{item.progressContent}</p>
                        <small>下一步：{item.nextAction}</small>
                        {(item.ownerName || item.dueDate) && (
                          <small className="table-subtitle">
                            {item.ownerName && `负责人：${item.ownerName}`}
                            {item.ownerName && item.dueDate && ' · '}
                            {item.dueDate && `计划完成：${item.dueDate}`}
                          </small>
                        )}
                        <small className="table-subtitle">记录于 {dateTime(item.occurredAt)}</small>
                        {item.correctsProgressId && (
                          <small className="table-subtitle">
                            更正记录，关联原进展 {item.correctsProgressId}
                          </small>
                        )}
                        {canProgress && (
                          <button
                            type="button"
                            className="text-button"
                            onClick={() => {
                              setCorrecting(item);
                              setContent(item.progressContent);
                              setNextAction(item.nextAction);
                              setProgressDate(item.progressDate ?? businessDate(item.occurredAt));
                              setOwnerName(item.ownerName ?? '');
                              setDueDate(item.dueDate ?? '');
                              setNotice('');
                            }}
                          >
                            更正此条
                          </button>
                        )}
                      </article>
                    ))
                  ) : (
                    <div className="empty-state">暂无进展</div>
                  )}
                </div>
              </div>
              <div className="panel">
                <h2>阶段里程碑</h2>
                <div className="timeline">
                  {milestones.length ? (
                    milestones.map((item) => (
                      <article key={item.id}>
                        <time>{dateTime(item.occurredAt)}</time>
                        <p>{label(item.stageCode)}</p>
                        <small>{item.descriptionText || '阶段记录'}</small>
                      </article>
                    ))
                  ) : (
                    <div className="empty-state">暂无里程碑</div>
                  )}
                </div>
              </div>
            </div>
            <div className={canCollaborate ? 'detail-grid' : undefined}>
              <ProjectDocuments
                projectId={detail.id}
                readOnly={!canCollaborate || detail.currentStageCode === 'TERMINATED'}
                onChanged={() => setDocumentsVersion((value) => value + 1)}
              />
              {canCollaborate && (
                <ProjectRequests
                  project={detail}
                  me={me}
                  reference={reference}
                  documentsVersion={documentsVersion}
                />
              )}
            </div>
            <ProjectHistory projectId={detail.id} me={me} reference={reference} />
            <ProjectOperationAuditPanel
              key={`operation-${detail.id}`}
              projectId={detail.id}
              revision={progress.length + detail.versionNo}
            />
            <ProjectInactivityAuditPanel
              key={detail.id}
              projectId={detail.id}
              revision={progress.length + detail.versionNo}
            />
          </>
        )
      )}
    </section>
  );
}
