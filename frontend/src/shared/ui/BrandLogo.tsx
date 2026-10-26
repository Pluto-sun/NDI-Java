/** 保留原图颜色和透明背景，通过展示容器消化图片自身留白。 */
export function BrandLogo({ login = false }: { login?: boolean }) {
  return (
    <div className={`brand brand-with-logo ${login ? 'brand-login' : ''}`}>
      <div className="brand-logo-frame">
        <img src="/brand-logo.png" alt="联通格物" />
      </div>
      <small>工业三化运营管理平台</small>
    </div>
  );
}
