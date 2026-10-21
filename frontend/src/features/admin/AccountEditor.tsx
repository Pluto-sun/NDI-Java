import { useEffect, useRef, useState } from 'react';
import { adminApi, type AdminAccount, type AdminConfiguration, type AccountValues } from './api';
import { Feedback } from '../../shared/ui/Feedback';

export function AccountEditor({
  user,
  configuration,
  onSaved,
  onCancel,
}: {
  user: AdminAccount | null;
  configuration: AdminConfiguration;
  onSaved: (user: AdminAccount) => Promise<void>;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<AccountValues>(() =>
    user
      ? {
          loginName: user.loginName,
          displayName: user.displayName,
          email: user.email ?? '',
          roleCodes: user.roleCodes,
          provinceId: user.roleCodes.includes('INTERFACE_REVIEWER') ? null : user.provinceId,
          cityId: user.cityId,
        }
      : {
          loginName: '',
          displayName: '',
          email: '',
          roleCodes: ['CITY_REPORTER'],
          provinceId: null,
          cityId: null,
        },
  );
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const title = useRef<HTMLHeadingElement>(null);
  const errorSummary = useRef<HTMLDivElement>(null);
  useEffect(() => {
    title.current?.focus();
  }, []);
  useEffect(() => {
    if (error) errorSummary.current?.focus();
  }, [error]);
  const scopes = configuration.roles
    .filter((role) => values.roleCodes.includes(role.code))
    .map((role) => role.scope);
  const hasProvince = scopes.some((scope) => ['PROVINCE', 'CITY'].includes(scope));
  const hasCity = scopes.includes('CITY');
  function changeRole(code: string, checked: boolean) {
    const role = configuration.roles.find((item) => item.code === code);
    const existingProvinceRoles = values.roleCodes.filter(
      (value) => configuration.roles.find((item) => item.code === value)?.scope === 'PROVINCE',
    );
    const roleCodes = checked
      ? role?.scope === 'PROVINCE'
        ? [...existingProvinceRoles, code]
        : [code]
      : values.roleCodes.filter((value) => value !== code);
    setValues({
      ...values,
      roleCodes,
      provinceId: ['NATIONAL', 'REVIEWER'].includes(role?.scope ?? '') ? null : values.provinceId,
      cityId: role?.scope === 'CITY' ? values.cityId : null,
    });
  }
  return (
    <form
      className="panel form-panel admin-editor"
      aria-labelledby="account-editor-title"
      aria-busy={busy}
      onSubmit={async (event) => {
        event.preventDefault();
        setError('');
        if (values.roleCodes.length === 0) {
          setError('请至少选择一个角色。');
          return;
        }
        setBusy(true);
        try {
          const result = user
            ? await adminApi.update(user, values)
            : await adminApi.create(values, password);
          setPassword('');
          await onSaved(result);
        } catch (cause) {
          setError(cause instanceof Error ? cause.message : '账号保存失败');
        } finally {
          setBusy(false);
        }
      }}
    >
      <h2 id="account-editor-title" tabIndex={-1} ref={title}>
        {user ? '编辑账号' : '创建账号'}
      </h2>
      <p>新账号使用 11 位手机号。管理员不参与业务审批；省分三类负责人允许在同一省分内兼任。</p>
      {error && (
        <div ref={errorSummary} tabIndex={-1}>
          <Feedback tone="error">{error}</Feedback>
        </div>
      )}
      <div className="form-grid">
        <label>
          登录账号
          <input
            name="username"
            autoComplete="username"
            required
            maxLength={100}
            pattern={user && values.loginName === user.loginName ? undefined : '1[0-9]{10}'}
            value={values.loginName}
            onChange={(event) => setValues({ ...values, loginName: event.target.value })}
          />
        </label>
        <label>
          账号姓名
          <input
            required
            maxLength={100}
            autoComplete="off"
            value={values.displayName}
            onChange={(event) => setValues({ ...values, displayName: event.target.value })}
          />
        </label>
        <label>
          账号邮箱（选填）
          <input
            type="email"
            maxLength={254}
            autoComplete="off"
            value={values.email}
            onChange={(event) => setValues({ ...values, email: event.target.value })}
          />
        </label>
        {!user && (
          <label>
            初始密码
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
        )}
      </div>
      <fieldset className="change-fieldset">
        <legend>账号角色</legend>
        <div className="admin-role-options">
          {configuration.roles.map((role) => (
            <label className="check-label" key={role.code}>
              <input
                type="checkbox"
                checked={values.roleCodes.includes(role.code)}
                onChange={(event) => changeRole(role.code, event.target.checked)}
              />
              {role.label}
            </label>
          ))}
        </div>
      </fieldset>
      {hasProvince && (
        <div className="form-grid">
          <label>
            {scopes.includes('REVIEWER') ? '当前负责省份（选填）' : '所属省分'}
            <select
              required={!scopes.includes('REVIEWER')}
              value={values.provinceId ?? ''}
              onChange={(event) =>
                setValues({ ...values, provinceId: event.target.value || null, cityId: null })
              }
            >
              <option value="">请选择省分</option>
              {configuration.provinces.map((province) => (
                <option key={province.id} value={province.id}>
                  {province.name}
                </option>
              ))}
            </select>
          </label>
          {hasCity && (
            <label>
              所属地市
              <select
                required
                value={values.cityId ?? ''}
                onChange={(event) => setValues({ ...values, cityId: event.target.value || null })}
              >
                <option value="">请选择地市</option>
                {configuration.cities
                  .filter((city) => city.provinceId === values.provinceId)
                  .map((city) => (
                    <option key={city.id} value={city.id}>
                      {city.name}
                    </option>
                  ))}
              </select>
            </label>
          )}
        </div>
      )}
      {scopes.includes('REVIEWER') && (
        <Feedback>
          接口人不绑定单个属地。创建账号后，请到“接口人范围”配置负责省份，支持多个省份；未配置时没有项目权限。
        </Feedback>
      )}
      <div className="form-actions">
        <button type="button" className="secondary-button" disabled={busy} onClick={onCancel}>
          取消编辑
        </button>
        <button className="primary-button" disabled={busy}>
          {busy ? '正在保存…' : user ? '保存账号' : '创建账号'}
        </button>
      </div>
    </form>
  );
}
