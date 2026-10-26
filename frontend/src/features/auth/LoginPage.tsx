import { BrandLogo } from '../../shared/ui/BrandLogo';
import { useState } from 'react';
import { Feedback } from '../../shared/ui/Feedback';

export function LoginPage({
  onLogin,
  message,
  connectionError,
}: {
  onLogin: (loginName: string, password: string) => Promise<void>;
  message: string;
  connectionError: string;
}) {
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  return (
    <main className="entry">
      <div className="entry-intro">
        <BrandLogo login />
        <h1>
          项目协作
          <br />
          从这里开始
        </h1>
        <p>统一管理项目、进展、审批和经营分析。请使用已启用的个人账号登录。</p>
      </div>
      <section className="entry-panel" aria-labelledby="login-title">
        <form
          className="entry-card"
          aria-busy={busy}
          onSubmit={async (event) => {
            event.preventDefault();
            setError('');
            setBusy(true);
            try {
              await onLogin(login, password);
              setPassword('');
            } catch (cause) {
              setError(cause instanceof Error ? cause.message : '登录失败');
            } finally {
              setBusy(false);
            }
          }}
        >
          <span className="eyebrow">NDI ACCOUNT</span>
          <h2 id="login-title">登录账号</h2>
          {message && <Feedback>{message}</Feedback>}
          {connectionError && (
            <Feedback tone="error">
              {connectionError}
              <button
                type="button"
                className="text-button"
                onClick={() => window.location.reload()}
              >
                重新连接
              </button>
            </Feedback>
          )}
          {error && <Feedback tone="error">{error}</Feedback>}
          <label htmlFor="login-name">账号</label>
          <input
            id="login-name"
            autoComplete="username"
            required
            maxLength={80}
            value={login}
            onChange={(event) => setLogin(event.target.value)}
          />
          <label htmlFor="login-password">密码</label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            required
            maxLength={128}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <button className="primary-button full" disabled={busy || !!connectionError}>
            {busy ? '正在登录…' : '登录'}
          </button>
          <p>账号未开通或忘记密码，请联系系统管理员。</p>
        </form>
      </section>
    </main>
  );
}
