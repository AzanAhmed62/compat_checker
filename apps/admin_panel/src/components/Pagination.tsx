interface PaginationProps {
  page: number;
  pageSize: number;
  total: number;
  onPageChange(page: number): void;
}

export function Pagination({ page, pageSize, total, onPageChange }: PaginationProps) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const from = total === 0 ? 0 : page * pageSize + 1;
  const to = Math.min(total, (page + 1) * pageSize);
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="muted">
        {from}–{to} of {total}
      </span>
      <div className="pagination-buttons">
        <button
          type="button"
          className="button secondary"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
        >
          Previous
        </button>
        <span aria-live="polite" className="page-count">
          Page {page + 1} of {pages}
        </span>
        <button
          type="button"
          className="button secondary"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= pages}
        >
          Next
        </button>
      </div>
    </nav>
  );
}
