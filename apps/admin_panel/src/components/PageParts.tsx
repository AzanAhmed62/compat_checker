import type { ReactNode } from 'react';

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {description ? <p className="muted">{description}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </header>
  );
}

export function ErrorPanel({ error, retry }: { error: unknown; retry?: () => void }) {
  const message = error instanceof Error ? error.message : String(error ?? 'Unknown error');
  return (
    <div className="notice error" role="alert">
      <div>
        <strong>Request failed.</strong> {message}
      </div>
      {retry ? (
        <button type="button" className="button secondary" onClick={retry}>
          Retry
        </button>
      ) : null}
    </div>
  );
}

export function LoadingPanel({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="notice" role="status" aria-live="polite">
      {label}
    </div>
  );
}

export function EmptyPanel({ children }: { children: ReactNode }) {
  return <div className="empty-state">{children}</div>;
}

export function Badge({ value }: { value: string }) {
  const tone = value.replaceAll('_', '-');
  return <span className={`badge badge-${tone}`}>{value.replaceAll('_', ' ')}</span>;
}
