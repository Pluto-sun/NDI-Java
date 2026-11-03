import './ListPagination.css';

/** 列表分页统一靠右排列，并在窄屏中按完整控件换行。 */
export function ListPagination({
  label,
  page,
  totalPages,
  totalElements,
  onPageChange,
}: {
  label: string;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <nav className="list-pagination" aria-label={`${label}分页`}>
      <span>共 {totalElements} 条</span>
      <div className="list-pagination-controls">
        <button
          type="button"
          className="secondary-button"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          上一页
        </button>
        <span>
          第 {page + 1} / {Math.max(1, totalPages)} 页
        </span>
        <button
          type="button"
          className="secondary-button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          下一页
        </button>
      </div>
    </nav>
  );
}
