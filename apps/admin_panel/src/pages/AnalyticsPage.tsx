import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  EmptyPanel,
  ErrorPanel,
  LoadingPanel,
  PageHeader,
} from '../components/PageParts';
import { fetchAnalytics } from '../lib/api';
import { formatDay } from '../lib/format';

export function AnalyticsPage() {
  const [days, setDays] = useState(30);
  const query = useQuery({
    queryKey: ['analytics', days],
    queryFn: () => fetchAnalytics(days),
  });

  const maxDaily = Math.max(
    1,
    ...(query.data?.daily.flatMap((day) => [day.views, day.searches, day.checker_runs]) ?? [1]),
  );

  return (
    <>
      <PageHeader
        title="Analytics"
        description="Anonymous, salted-session event aggregates. The service stores no raw IP addresses, user identifiers, or free-form search PII beyond the search term."
        actions={
          <select
            aria-label="Analytics window"
            value={days}
            onChange={(event) => setDays(Number(event.target.value))}
          >
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
            <option value={90}>Last 90 days</option>
            <option value={365}>Last year</option>
          </select>
        }
      />

      {query.isLoading ? <LoadingPanel label="Loading analytics…" /> : null}
      {query.error ? (
        <ErrorPanel error={query.error} retry={() => void query.refetch()} />
      ) : null}
      {query.data ? (
        <>
          <div className="stat-grid">
            <div className="stat-card">
              <span className="stat-value">{query.data.totals.events}</span>
              <span className="stat-label">Events</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{query.data.totals.product_views}</span>
              <span className="stat-label">Product views</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{query.data.totals.searches}</span>
              <span className="stat-label">Searches</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{query.data.totals.checker_runs}</span>
              <span className="stat-label">Checker runs</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{query.data.totals.unique_sessions}</span>
              <span className="stat-label">Unique sessions</span>
            </div>
          </div>

          <section className="card" aria-labelledby="daily-heading">
            <div className="card-header">
              <h2 id="daily-heading">Daily activity</h2>
              <div className="legend">
                <span className="legend-views">Views</span>
                <span className="legend-searches">Searches</span>
                <span className="legend-checks">Checker</span>
              </div>
            </div>
            {query.data.daily.length === 0 ? (
              <EmptyPanel>No events have been recorded in this window.</EmptyPanel>
            ) : (
              <div className="chart" role="img" aria-label="Daily views, searches, and checker runs">
                {query.data.daily.map((day) => (
                  <div className="chart-column" key={day.date}>
                    <div className="chart-bars">
                      <span
                        className="bar views"
                        style={{ height: `${(day.views / maxDaily) * 100}%` }}
                        title={`${day.views} views`}
                      />
                      <span
                        className="bar searches"
                        style={{ height: `${(day.searches / maxDaily) * 100}%` }}
                        title={`${day.searches} searches`}
                      />
                      <span
                        className="bar checks"
                        style={{ height: `${(day.checker_runs / maxDaily) * 100}%` }}
                        title={`${day.checker_runs} checker runs`}
                      />
                    </div>
                    <span className="chart-label">{formatDay(day.date)}</span>
                  </div>
                ))}
              </div>
            )}
          </section>

          <div className="two-column">
            <section className="card" aria-labelledby="top-products-heading">
              <div className="card-header">
                <h2 id="top-products-heading">Most viewed products</h2>
              </div>
              {query.data.top_products.length === 0 ? (
                <EmptyPanel>No product views yet.</EmptyPanel>
              ) : (
                <ol className="ranked-list">
                  {query.data.top_products.map((product) => (
                    <li key={product.entity_id}>
                      <span>{product.name ?? 'Deleted product'}</span>
                      <strong>{product.views} views</strong>
                    </li>
                  ))}
                </ol>
              )}
            </section>
            <section className="card" aria-labelledby="top-searches-heading">
              <div className="card-header">
                <h2 id="top-searches-heading">Top searches</h2>
              </div>
              {query.data.top_searches.length === 0 ? (
                <EmptyPanel>No searches yet.</EmptyPanel>
              ) : (
                <ol className="ranked-list">
                  {query.data.top_searches.map((term) => (
                    <li key={term.term}>
                      <span>{term.term}</span>
                      <strong>{term.count}×</strong>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </>
      ) : null}
    </>
  );
}
