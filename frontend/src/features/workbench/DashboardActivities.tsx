import { MonthPicker } from '../../shared/ui/MonthPicker';
import { TruncatedText } from '../../shared/ui/TruncatedText';
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
          <div className="month-filter-field">
            变动月份起始
            <MonthPicker
              aria-label="变动月份起始"
              value={from}
              max={to}
              required
              onChange={(selectedMonth) => setFrom(selectedMonth)}
            />
          </div>
          <div className="month-filter-field">
            变动月份结束
            <MonthPicker
              aria-label="变动月份结束"
              value={to}
              min={from}
              required
              onChange={(selectedMonth) => setTo(selectedMonth)}
            />
          </div>
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
        <table aria-label="近期项目变动">
          <colgroup>
            {[154, 96, 235, 120, 170, 130, 200, 130, 172, 172, 170].map((width, index) => (
              <col key={index} style={{ width }} />
            ))}
          </colgroup>
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
                      : ['BIG_DATA', 'WANGWU'].includes(item.code)
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
                  <td>
                    <TruncatedText text={dateTime(row.occurredAt)} />
                  </td>
                  <td>
                    <span className="status-pill">{eventLabels[row.eventType] ?? '项目变更'}</span>
                  </td>
                  <th>
                    <TruncatedText text={row.projectName}>
                      <Link className="inline-link" to={`/projects/${row.projectId}`}>
                        {row.projectName}
                      </Link>
                    </TruncatedText>
                  </th>
                  <td>
                    <TruncatedText text={row.province} />
                  </td>
                  <td>
                    <TruncatedText text={row.industry} />
                  </td>
                  <td>
                    <TruncatedText text={categories.join('、') || '—'} />
                  </td>
                  <td>
                    <TruncatedText text={products.map((item) => item.label).join('、') || '—'} />
                  </td>
                  <td>
                    <TruncatedText
                      text={delta === null ? '—' : `${delta > 0 ? '+' : ''}${amount(delta)}`}
                    />
                  </td>
                  <td>
                    <TruncatedText
                      text={
                        stageName(row.beforeStage) +
                        (row.beforeAmount === null ? '' : ` / ${amount(row.beforeAmount)} 万元`)
                      }
                    />
                  </td>
                  <td>
                    <TruncatedText
                      text={
                        stageName(row.afterStage) +
                        (row.afterAmount === null ? '' : ` / ${amount(row.afterAmount)} 万元`)
                      }
                    />
                  </td>
                  <td>
                    <TruncatedText text={row.leaderName} />
                  </td>
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
