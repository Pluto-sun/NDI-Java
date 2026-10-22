import { useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../projects/api';
import { useLiveQuery } from '../../shared/api/useLiveQuery';
import { Feedback, Loading } from '../../shared/ui/Feedback';
import { dateTime } from '../../shared/ui/format';

const PAGE_SIZE = 20;

export function RecentActivitiesCard() {
  const {
    data: items,
    busy,
    error,
    refresh,
  } = useLiveQuery(projectsApi.recentActivities, '近期项目变动加载失败');
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  return (
    <section
      className="panel recent-activities"
      aria-labelledby="recent-activities-title"
      aria-busy={busy}
    >
      <div className="panel-top">
        <div>
          <h2 id="recent-activities-title">近期项目变动</h2>
          <p className="subtle">近 30 天已生效的项目操作，按当前数据权限展示，每 30 秒自动刷新。</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          disabled={busy}
          onClick={() => {
            setVisibleCount(PAGE_SIZE);
            void refresh();
          }}
        >
          {busy ? '刷新中…' : '刷新动态'}
        </button>
      </div>
      {error && (
        <Feedback tone="error">
          {error}
          {items && ' 当前保留上次成功结果，数据未更新。'}
        </Feedback>
      )}
      {items === null ? (
        !error && <Loading />
      ) : items.length === 0 ? (
        <p className="subtle">近 30 天暂无项目变动。</p>
      ) : (
        <>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">时间</th>
                  <th scope="col">项目</th>
                  <th scope="col">属地</th>
                  <th scope="col">变动</th>
                  <th scope="col">操作人</th>
                </tr>
              </thead>
              <tbody>
                {items.slice(0, visibleCount).map((item, index) => (
                  <tr key={`${item.projectId}-${item.occurredAt}-${index}`}>
                    <td>{dateTime(item.occurredAt)}</td>
                    <td>
                      <Link className="table-link" to={`/projects/${item.projectId}`}>
                        {item.projectName}
                      </Link>
                      <small className="table-subtitle">{item.projectCode}</small>
                    </td>
                    <td>
                      {item.province} · {item.city}
                    </td>
                    <td>{item.summary}</td>
                    <td>{item.operatorName}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {items.length > visibleCount && (
            <button
              type="button"
              className="secondary-button recent-activities-more"
              onClick={() => setVisibleCount((count) => count + PAGE_SIZE)}
            >
              加载更多（剩余 {items.length - visibleCount} 条）
            </button>
          )}
        </>
      )}
    </section>
  );
}
