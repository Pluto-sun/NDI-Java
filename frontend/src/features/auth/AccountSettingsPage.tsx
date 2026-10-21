import { useState } from 'react';
import { authApi, type AccountProfile } from './api';
import { Feedback } from '../../shared/ui/Feedback';

export function AccountSettingsPage({
  profile,
  onUpdated,
  onPasswordChanged,
}: {
  profile: AccountProfile;
  onUpdated: (user: AccountProfile) => void;
  onPasswordChanged: () => void;
}) {
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [email, setEmail] = useState(profile.email ?? '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [profileError, setProfileError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState<'profile' | 'password' | null>(null);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">ACCOUNT</span>
          <h1>个人设置</h1>
          <p>修改个人资料与登录密码。角色及数据范围由管理员维护。</p>
        </div>
      </div>
      <form
        className="panel form-panel"
        aria-busy={busy === 'profile'}
        onSubmit={async (event) => {
          event.preventDefault();
          setProfileError('');
          setSaved(false);
          setBusy('profile');
          try {
            const updated = await authApi.update(displayName, email);
            onUpdated(updated);
            setDisplayName(updated.displayName);
            setEmail(updated.email ?? '');
            setSaved(true);
          } catch (cause) {
            setProfileError(cause instanceof Error ? cause.message : '资料保存失败');
          } finally {
            setBusy(null);
          }
        }}
      >
        <h2>基本资料</h2>
        <p>登录账号：{profile.loginName}</p>
        <div className="form-grid">
          <label>
            姓名
            <input
              required
              maxLength={100}
              autoComplete="name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </label>
          <label>
            邮箱（选填）
            <input
              type="email"
              maxLength={254}
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
        </div>
        {profileError && <Feedback tone="error">{profileError}</Feedback>}
        {saved && <Feedback tone="success">个人资料已保存。</Feedback>}
        <div className="form-actions">
          <button className="primary-button" disabled={busy !== null}>
            {busy === 'profile' ? '正在保存…' : '保存资料'}
          </button>
        </div>
      </form>
      <form
        className="panel form-panel account-password-panel"
        aria-busy={busy === 'password'}
        onSubmit={async (event) => {
          event.preventDefault();
          setPasswordError('');
          if (newPassword !== confirmPassword) {
            setPasswordError('两次输入的新密码不一致。');
            return;
          }
          if (newPassword === currentPassword) {
            setPasswordError('新密码不能与当前密码相同。');
            return;
          }
          setBusy('password');
          try {
            await authApi.changePassword(currentPassword, newPassword);
            setCurrentPassword('');
            setNewPassword('');
            setConfirmPassword('');
            onPasswordChanged();
          } catch (cause) {
            setPasswordError(cause instanceof Error ? cause.message : '密码修改失败');
          } finally {
            setBusy(null);
          }
        }}
      >
        <h2>修改密码</h2>
        <p id="password-help">
          新密码为 6 至 128 个字符。修改成功后，所有已登录设备都会退出，需要重新登录。
        </p>
        <input
          type="text"
          name="username"
          autoComplete="username"
          value={profile.loginName}
          readOnly
          hidden
        />
        <div className="form-grid">
          <label>
            当前密码
            <input
              type="password"
              autoComplete="current-password"
              required
              maxLength={128}
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </label>
          <label>
            新密码
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={6}
              maxLength={128}
              aria-describedby="password-help"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </label>
          <label>
            确认新密码
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={6}
              maxLength={128}
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
            />
          </label>
        </div>
        {passwordError && <Feedback tone="error">{passwordError}</Feedback>}
        <div className="form-actions">
          <button className="primary-button" disabled={busy !== null}>
            {busy === 'password' ? '正在修改…' : '修改密码并重新登录'}
          </button>
        </div>
      </form>
    </>
  );
}
