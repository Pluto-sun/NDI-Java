export function ModulePlaceholder({ title }: { title: string }) {
  return (
    <section>
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
