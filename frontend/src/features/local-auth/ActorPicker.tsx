import { useState } from 'react';
import type { ProjectUser } from '../projects/types';
import { Feedback } from '../../shared/ui/Feedback';

export function ActorPicker({
  actors,
  error,
  onSelect,
  onRetry,
}: {
  actors: ProjectUser[];
  error: string;
  onSelect: (id: string) => Promise<void>;
  onRetry: () => void;
}) {
  const [selected, setSelected] = useState('');
  const [busy, setBusy] = useState(false);
  return (
    <main className="entry">
      <div className="entry-intro">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            N
          </span>
          <span>
            NDI<small>工业三化运营管理平台</small>
          </span>
        </div>
        <h1>
          项目管理
          <br />
          从清晰的业务流程开始
        </h1>
        <p>
          项目主档、进展、审批与经营数据在同一模块协作。本页面沿用旧系统的业务视觉语言，正在逐页接入新的
          Java 服务。
        </p>
      </div>
      <section className="entry-panel" aria-labelledby="entry-title">
        <div className="entry-card">
          <span className="eyebrow">LOCAL DEVELOPMENT</span>
          <h2 id="entry-title">选择开发测试账号</h2>
          <p>仅在本地环境用于接口联调。生产环境将接入正式认证。</p>
          {error && <Feedback tone="error">{error}</Feedback>}
          {actors.length === 0 ? (
            <>
              <Feedback>
                当前数据库没有已启用账号。请运行本地演示数据脚本，或导入已有测试账号。
              </Feedback>
              <button type="button" className="secondary-button" onClick={onRetry}>
                重新检查连接
              </button>
            </>
          ) : (
            <>
              <label htmlFor="actor">账号</label>
              <select
                id="actor"
                value={selected}
                onChange={(event) => setSelected(event.target.value)}
              >
                <option value="">请选择账号</option>
                {actors.map((actor) => (
                  <option key={actor.id} value={actor.id}>
                    {actor.displayName} · {actor.roleCodes.join('、')}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="primary-button full"
                disabled={!selected || busy}
                onClick={async () => {
                  setBusy(true);
                  await onSelect(selected);
                  setBusy(false);
                }}
              >
                {busy ? '正在进入…' : '进入项目管理'}
              </button>
            </>
          )}
        </div>
      </section>
    </main>
  );
}
