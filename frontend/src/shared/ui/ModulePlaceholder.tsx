export function ModulePlaceholder({ title }: { title: string }) {
  return (
    <section>
      <div className="page-heading">
        <div>
          <span className="eyebrow">MODULE</span>
          <h1>{title}</h1>
        </div>
      </div>
      <div className="panel module-placeholder">
        <span className="placeholder-mark" aria-hidden="true">
          ◇
        </span>
        <h2>{title}</h2>
        <p>模块建设中，后续将在此提供相关功能。</p>
      </div>
    </section>
  );
}
