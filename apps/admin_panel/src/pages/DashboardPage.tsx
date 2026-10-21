import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchDashboard } from '../lib/api';
import { formatDate, titleCase, truncate } from '../lib/format';
import { Badge, ErrorPanel, LoadingPanel, PageHeader } from '../components/PageParts';
import type { DashboardCounts } from '../lib/types';

const countLabels: Array<[keyof DashboardCounts, string, string]> = [
  ['users_active', 'Active users', '/users'],
  ['products_published', 'Published products', '/catalog'],
  ['rules_published', 'Published rules', '/rules'],
  ['contributions_pending', 'Pending contributions', '/contributions'],
  ['reports_open', 'Open reports', '/reports'],
  ['requests_open', 'Open requests', '/requests'],
  ['comments_hidden', 'Hidden comments', '/comments'],
  ['products_draft', 'Draft products', '/catalog'],
  ['rules_draft', 'Draft rules', '/rules'],
  ['users_banned', 'Banned users', '/users'],
  ['users_suspended', 'Suspended users', '/users'],
  ['notifications_unread', 'Unread notifications', '/users'],
];

export function DashboardPage() {
  const query = useQuery({ queryKey: ['dashboard'], queryFn: fetchDashboard });

  if (query.isLoading) return <LoadingPanel label="Loading dashboard…" />;
  if (query.error) return <ErrorPanel error={query.error} retry={() => void query.refetch()} />;
  const data = query.data;
  if (!data) return <ErrorPanel error="The dashboard returned no data." />;

  return (
    <>
      <PageHeader
        title="Operations dashboard"
        description="Current moderation load and catalog health. Counts are computed server-side under an administrator role check."
        actions={
          <button
            type="button"
            className="button secondary"
            onClick={() => void query.refetch()}
            disabled={query.isFetching}
          >
            {query.isFetching ? 'Refreshing…' : 'Refresh'}
          </button>
        }
      />

      <section aria-labelledby="counts-heading">
        <h2 id="counts-heading" className="visually-hidden">
          Operational counts
        </h2>
        <div className="stat-grid">
          {countLabels.map(([key, label, link]) => (
            <Link key={key} to={link} className="stat-card">
              <span className="stat-value">{data.counts[key] ?? 0}</span>
              <span className="stat-label">{label}</span>
            </Link>
          ))}
        </div>
      </section>

      <div className="two-column">
        <section className="card" aria-labelledby="pending-heading">
          <div className="card-header">
            <h2 id="pending-heading">Oldest pending contributions</h2>
            <Link to="/contributions">Open queue</Link>
          </div>
          {data.pending_contributions.length === 0 ? (
            <p className="muted">No contributions are waiting for review.</p>
          ) : (
            <ul className="compact-list">
              {data.pending_contributions.map((item) => (
                <li key={item.id}>
                  <div>
                    <Badge value={item.kind} />{' '}
                    <strong>{item.contributor_name ?? 'Unknown contributor'}</strong>
                    <p className="muted small">
                      {truncate(String(item.payload.summary ?? item.payload.name ?? item.id), 100)}
                    </p>
                  </div>
                  <time dateTime={item.created_at}>{formatDate(item.created_at)}</time>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card" aria-labelledby="reports-heading">
          <div className="card-header">
            <h2 id="reports-heading">Open reports</h2>
            <Link to="/reports">Open queue</Link>
          </div>
          {data.open_reports.length === 0 ? (
            <p className="muted">No reports need attention.</p>
          ) : (
            <ul className="compact-list">
              {data.open_reports.map((item) => (
                <li key={item.id}>
                  <div>
                    <Badge value={item.status} />{' '}
                    <strong>{titleCase(item.reason)}</strong>
                    <p className="muted small">
                      {item.target_type} · {item.reporter_name ?? 'Unknown reporter'}
                    </p>
                  </div>
                  <time dateTime={item.created_at}>{formatDate(item.created_at)}</time>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="card" aria-labelledby="audit-heading">
        <div className="card-header">
          <h2 id="audit-heading">Recent administrative actions</h2>
          <Link to="/audit">Full audit log</Link>
        </div>
        {data.recent_audit.length === 0 ? (
          <p className="muted">No administrative actions recorded yet.</p>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Action</th>
                  <th scope="col">Entity</th>
                  <th scope="col">Actor</th>
                  <th scope="col">When</th>
                </tr>
              </thead>
              <tbody>
                {data.recent_audit.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <code>{item.action}</code>
                    </td>
                    <td>{item.entity_type}</td>
                    <td>{item.actor ?? 'System'}</td>
                    <td>{formatDate(item.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
