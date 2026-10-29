import { BrandLogo } from '../../shared/ui/BrandLogo';
import { Select } from '../../shared/ui/Select';
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
        <BrandLogo login />
        <h1>
          项目管理
          <br />
          从清晰的业务流程开始
        </h1>
        <p>项目管理、计收管理、排产管理与能力指标分模块协作，经营分析与业务办理统一呈现。</p>
      </div>
      <section className="entry-panel" aria-labelledby="entry-title">
        <div className="entry-card">
          <span className="eyebrow">ACCOUNT ACCESS</span>
          <h2 id="entry-title">选择账号</h2>
          <p>选择账号进入平台，菜单和数据范围由账号角色决定。</p>
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
              <Select
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
              </Select>
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
