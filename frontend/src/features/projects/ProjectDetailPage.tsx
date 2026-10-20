import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { projectsApi } from './api';
import type {
  ProjectDetail,
  ProjectMilestone,
  ProjectProgress,
  ProjectUser,
  ReferenceData,
} from './types';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount, dateTime } from '../../shared/ui/format';
import { ProjectDocuments } from './ProjectDocuments';
import { ProjectRequests } from './ProjectRequests';

export function ProjectDetailPage({
  reference,
  me,
}: {
  reference: ReferenceData;
  me: ProjectUser;
}) {
  const { id } = useParams();
  const [detail, setDetail] = useState<ProjectDetail | null>(null);
  const [progress, setProgress] = useState<ProjectProgress[]>([]);
  const [milestones, setMilestones] = useState<ProjectMilestone[]>([]);
  const [leaders, setLeaders] = useState<ProjectUser[]>([]);
  const [content, setContent] = useState('');
  const [nextAction, setNextAction] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [documentsVersion, setDocumentsVersion] = useState(0);

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
      void projectsApi
        .provinceLeaders(project.provinceId)
        .then(setLeaders)
        .catch(() => setLeaders([]));
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
    try {
      await projectsApi.appendProgress(id, content.trim(), nextAction.trim());
      setContent('');
      setNextAction('');
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '进展保存失败');
    } finally {
      setSaving(false);
    }
  }

  const label = (code: string) =>
    reference.stages.find((item) => item.code === code)?.label ?? code;
  const canCollaborate =
    me.provinceId === detail?.provinceId &&
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
          <Link className="back-link" to="/projects">
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
      {loading ? (
        <Loading />
      ) : (
        detail && (
          <>
            <div className="detail-grid">
              <div className="panel">
                <h2>项目主档</h2>
                <dl className="facts">
                  <div>
                    <dt>客户名称</dt>
                    <dd>{detail.customerName}</dd>
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
                    <dt>省分负责人</dt>
                    <dd>
                      {leaders.find((item) => item.id === detail.provinceLeaderUserId)
                        ?.displayName ?? '负责人账号不可用'}
                    </dd>
                  </div>
                  <div>
                    <dt>地市客户经理</dt>
                    <dd>{detail.cityAccountManagerName}</dd>
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
            <div className="detail-grid">
              <div className="panel">
                <h2>项目进展</h2>
                {canProgress && (
                  <form className="inline-form" onSubmit={append}>
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
                    <button type="submit" className="primary-button" disabled={saving}>
                      {saving ? '保存中…' : '追加进展'}
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
                        <time>{dateTime(item.occurredAt)}</time>
                        <p>{item.progressContent}</p>
                        <small>下一步：{item.nextAction}</small>
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
            {canCollaborate && (
              <div className="detail-grid">
                <ProjectDocuments
                  projectId={detail.id}
                  readOnly={detail.currentStageCode === 'TERMINATED'}
                  onChanged={() => setDocumentsVersion((value) => value + 1)}
                />
                <ProjectRequests
                  project={detail}
                  me={me}
                  reference={reference}
                  documentsVersion={documentsVersion}
                />
              </div>
            )}
          </>
        )
      )}
    </section>
  );
}
