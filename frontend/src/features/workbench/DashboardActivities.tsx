import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { amount, dateTime } from '../../shared/ui/format';
import { PROJECT_STAGE_TABS } from '../../shared/ui/ProjectStageTabs';
import { dashboardApi, dashboardProducts, type DashboardFilter } from './dashboardApi';

const eventLabels: Record<string, string> = {
  PROJECT_CREATED: '新增',
  PROJECT_PROFILE_UPDATED: '资料变更',
  STAGE_ADVANCEMENT_APPROVED: '阶段推进',
  MAJOR_CHANGE_APPROVED: '重大变更',
  PROJECT_PEOPLE_UPDATED: '人员配置',
};
const stageName = (code: string | null) =>
  code === 'TERMINATED'
    ? '已终止'
    : (PROJECT_STAGE_TABS.find((item) => item.code === code)?.label ?? '—');
export function DashboardActivities({
  filter,
  stageLabel,
}: {
  filter: DashboardFilter;
  stageLabel: string;
}) {
  const [from, setFrom] = useState(filter.from),
    [to, setTo] = useState(filter.to);
  const [range, setRange] = useState({ from, to });
  const load = useCallback(
    (signal: AbortSignal) => dashboardApi.activities({ ...filter, ...range }, signal),
    [filter, range],
  );
  const { data, busy, error } = useLiveQuery(load, '近期变动加载失败');
  return (
    <section className="panel dashboard-activities">
      <div className="dashboard-panel-heading">
        <h2>近期项目变动情况{stageLabel}</h2>
        <form
          className="dashboard-inline-filters"
          aria-label="近期变动时间筛选"
          onSubmit={(event) => {
            event.preventDefault();
            if (from && to && from <= to) setRange({ from, to });
          }}
        >
          <label>
            变动月份起始
            <input
              type="month"
              value={from}
              max={to}
              required
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label>
            变动月份结束
            <input
              type="month"
              value={to}
              min={from}
              required
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
          <button className="secondary-button" disabled={busy}>
            查询变动
          </button>
        </form>
      </div>
      <p className="subtle">
        按变动发生月份查询最近 100
        条已生效事件；待审批申请不计入。省份、行业、分类、责任人是当前主档信息，前后阶段与金额取生效事件；旧事件缺失值以“—”显示。
      </p>
      {error && <Feedback tone="error">{error}</Feedback>}
      {busy && !data && <Loading />}
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>变动时间</th>
              <th>变动类型</th>
              <th>项目名称</th>
              <th>省份</th>
              <th>所属行业</th>
              <th>三化类别</th>
              <th>产品</th>
              <th>变动金额（万元）</th>
              <th>变动前状态</th>
              <th>变动后状态</th>
              <th>项目负责人</th>
            </tr>
          </thead>
          <tbody>
            {data?.map((row) => {
              const products = dashboardProducts.filter((item) =>
                row.products?.split(',').includes(item.code),
              );
              const categories = [
                ...new Set(
                  products.map((item) =>
                    item.code === 'NETWORK'
                      ? '网络化'
                      : item.code === 'WANGWU'
                        ? '智能化'
                        : '数字化',
                  ),
                ),
              ];
              const delta =
                row.afterAmount === null
                  ? null
                  : row.beforeAmount === null
                    ? row.eventType === 'PROJECT_CREATED'
                      ? row.afterAmount
                      : null
                    : row.afterAmount - row.beforeAmount;
              return (
                <tr key={row.id}>
                  <td>{dateTime(row.occurredAt)}</td>
                  <td>
                    <span className="status-pill">{eventLabels[row.eventType] ?? '项目变更'}</span>
                  </td>
                  <th>
                    <Link className="inline-link" to={`/projects/${row.projectId}`}>
                      {row.projectName}
                    </Link>
                  </th>
                  <td>{row.province}</td>
                  <td>{row.industry}</td>
                  <td>{categories.join('、') || '—'}</td>
                  <td>{products.map((item) => item.label).join('、') || '—'}</td>
                  <td>{delta === null ? '—' : `${delta > 0 ? '+' : ''}${amount(delta)}`}</td>
                  <td>
                    {stageName(row.beforeStage)}
                    {row.beforeAmount !== null && ` / ${amount(row.beforeAmount)} 万元`}
                  </td>
                  <td>
                    {stageName(row.afterStage)}
                    {row.afterAmount !== null && ` / ${amount(row.afterAmount)} 万元`}
                  </td>
                  <td>{row.leaderName}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {data?.length === 0 && <p className="empty-state">所选月份及阶段暂无已生效变动</p>}
    </section>
  );
}
