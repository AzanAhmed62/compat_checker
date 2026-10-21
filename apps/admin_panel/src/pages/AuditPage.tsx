import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Pagination } from '../components/Pagination';
import {
  EmptyPanel,
  ErrorPanel,
  LoadingPanel,
  PageHeader,
} from '../components/PageParts';
import { listAudit } from '../lib/api';
import { formatDate, prettyJson } from '../lib/format';

const entityTypes = [
  'profiles',
  'products',
  'compatibility_rules',
  'rule_evidence',
  'contributions',
  'reports',
  'comments',
  'product_requests',
  'guides',
  'media_assets',
];

export function AuditPage() {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const [appliedAction, setAppliedAction] = useState('');
  const [entityType, setEntityType] = useState('');

  const audit = useQuery({
    queryKey: ['audit', page, appliedAction, entityType],
    queryFn: () => listAudit({ page, action: appliedAction, entityType }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedAction(action.trim());
  }

  return (
    <>
      <PageHeader
        title="Audit log"
        description="Append-only administrative trail. Read-only; even administrators cannot edit or delete audit entries through the API."
      />
      <form className="filter-bar" onSubmit={submit} role="search">
        <label>
          Action contains
          <input
            value={action}
            onChange={(event) => setAction(event.target.value)}
            placeholder="e.g. contribution"
          />
        </label>
        <label>
          Entity type
          <select
            value={entityType}
            onChange={(event) => {
              setEntityType(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All entities</option>
            {entityTypes.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="button secondary">
          Apply
        </button>
      </form>

      {audit.isLoading ? <LoadingPanel label="Loading audit entries…" /> : null}
      {audit.error ? <ErrorPanel error={audit.error} retry={() => void audit.refetch()} /> : null}
      {audit.data && audit.data.items.length === 0 ? (
        <EmptyPanel>No audit entries match these filters.</EmptyPanel>
      ) : null}
      {audit.data && audit.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">When</th>
                  <th scope="col">Actor</th>
                  <th scope="col">Action</th>
                  <th scope="col">Entity</th>
                  <th scope="col">Details</th>
                </tr>
              </thead>
              <tbody>
                {audit.data.items.map((row) => (
                  <tr key={row.id}>
                    <td>{formatDate(row.created_at)}</td>
                    <td>{row.actor_name ?? 'System'}</td>
                    <td>
                      <code>{row.action}</code>
                    </td>
                    <td>
                      {row.entity_type}
                      {row.entity_id ? (
                        <div className="muted small">
                          <code>{row.entity_id}</code>
                        </div>
                      ) : null}
                    </td>
                    <td>
                      <details>
                        <summary>Before / after</summary>
                        <div className="audit-diff">
                          <div>
                            <strong>Before</strong>
                            <pre>{prettyJson(row.before_data) || '—'}</pre>
                          </div>
                          <div>
                            <strong>After</strong>
                            <pre>{prettyJson(row.after_data) || '—'}</pre>
                          </div>
                        </div>
                        {row.context ? (
                          <>
                            <strong>Context</strong>
                            <pre>{prettyJson(row.context)}</pre>
                          </>
                        ) : null}
                      </details>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={audit.data.limit}
            total={audit.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}
    </>
  );
}
