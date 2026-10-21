import { useEffect, useRef, useState } from 'react';
import { adminApi, type AdminAccount } from './api';
import { Feedback } from '../../shared/ui/Feedback';

export function PasswordResetForm({
  user,
  isSelf,
  onSaved,
  onCancel,
}: {
  user: AdminAccount;
  isSelf: boolean;
  onSaved: () => Promise<void>;
  onCancel: () => void;
}) {
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const title = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    title.current?.focus();
  }, []);
  return (
    <form
      className="panel form-panel admin-editor"
      aria-busy={busy}
      onSubmit={async (event) => {
        event.preventDefault();
        setError('');
        if (password !== confirmation) {
          setError('两次输入的重置密码不一致。');
          return;
        }
        setBusy(true);
        try {
          await adminApi.reset(user, password);
          setPassword('');
          setConfirmation('');
          await onSaved();
        } catch (cause) {
          setError(cause instanceof Error ? cause.message : '密码重置失败');
        } finally {
          setBusy(false);
        }
      }}
    >
      <h2 tabIndex={-1} ref={title}>
        重置账号密码
      </h2>
      <p>
        {user.displayName}（{user.loginName}）的所有旧会话将失效。
        {isSelf
          ? '你正在重置自己的密码，提交后需要重新登录。'
          : '请通过安全渠道告知账号本人新密码。'}
      </p>
      {error && <Feedback tone="error">{error}</Feedback>}
      <input type="text" autoComplete="username" value={user.loginName} readOnly hidden />
      <div className="form-grid">
        <label>
          重置密码
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={6}
            maxLength={128}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <label>
          确认重置密码
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={6}
            maxLength={128}
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          />
        </label>
      </div>
      <div className="form-actions">
        <button className="secondary-button" type="button" disabled={busy} onClick={onCancel}>
          取消重置
        </button>
        <button className="primary-button" disabled={busy}>
          {busy ? '正在重置…' : '确认重置密码'}
        </button>
      </div>
    </form>
  );
}
