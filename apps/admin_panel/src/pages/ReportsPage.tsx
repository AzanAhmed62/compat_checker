import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Modal } from '../components/Modal';
import { Pagination } from '../components/Pagination';
import {
  Badge,
  EmptyPanel,
  ErrorPanel,
  LoadingPanel,
  PageHeader,
} from '../components/PageParts';
import { listReports, resolveReport } from '../lib/api';
import { formatDate, titleCase, truncate } from '../lib/format';
import type { Report, ReportStatus } from '../lib/types';

export function ReportsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<ReportStatus | ''>('open');
  const [selected, setSelected] = useState<Report | null>(null);

  const reports = useQuery({
    queryKey: ['reports', page, status],
    queryFn: () => listReports({ page, status }),
  });

  async function completed() {
    setSelected(null);
    await queryClient.invalidateQueries({ queryKey: ['reports'] });
    await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  }

  return (
    <>
      <PageHeader
        title="Reports"
        description="Resolve reports with an explicit note. A moderation action is applied in the same database transaction as the report resolution."
        actions={
          <select
            aria-label="Filter report status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ReportStatus | '');
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option value="open">Open</option>
            <option value="investigating">Investigating</option>
            <option value="resolved">Resolved</option>
            <option value="dismissed">Dismissed</option>
          </select>
        }
      />

      {reports.isLoading ? <LoadingPanel label="Loading reports…" /> : null}
      {reports.error ? (
        <ErrorPanel error={reports.error} retry={() => void reports.refetch()} />
      ) : null}
      {reports.data && reports.data.items.length === 0 ? (
        <EmptyPanel>No reports match this filter.</EmptyPanel>
      ) : null}
      {reports.data && reports.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Report</th>
                  <th scope="col">Target</th>
                  <th scope="col">Reporter</th>
                  <th scope="col">Status</th>
                  <th scope="col">Created</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {reports.data.items.map((report) => (
                  <tr key={report.id}>
                    <td>
                      <strong>{titleCase(report.reason)}</strong>
                      <div className="muted small">{truncate(report.details, 110)}</div>
                    </td>
                    <td>
                      {titleCase(report.target_type)}
                      <div className="muted small">
                        <code>{report.target_id}</code>
                      </div>
                    </td>
                    <td>{report.reporter_name ?? 'Deleted user'}</td>
                    <td>
                      <Badge value={report.status} />
                      {report.handler_name ? (
                        <div className="muted small">by {report.handler_name}</div>
                      ) : null}
                    </td>
                    <td>{formatDate(report.created_at)}</td>
                    <td>
                      <button
                        type="button"
                        className="button small secondary"
                        onClick={() => setSelected(report)}
                      >
                        Review
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={reports.data.limit}
            total={reports.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {selected ? (
        <ResolveReport report={selected} onClose={() => setSelected(null)} onDone={() => void completed()} />
      ) : null}
    </>
  );
}

function ResolveReport({
  report,
  onClose,
  onDone,
}: {
  report: Report;
  onClose(): void;
  onDone(): void;
}) {
  const [status, setStatus] = useState<Exclude<ReportStatus, 'open'>>(
    report.status === 'dismissed' ? 'dismissed' : 'resolved',
  );
  const [note, setNote] = useState('');
  const [action, setAction] = useState<'none' | 'hide_comment' | 'retire_rule' | 'archive_product'>(
    'none',
  );
  const [error, setError] = useState<string | null>(null);
  const resolve = useMutation({
    mutationFn: () =>
      resolveReport({
        id: report.id,
        status,
        note,
        action,
      }),
    onSuccess: onDone,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  const actionApplies =
    (action === 'hide_comment' && report.target_type === 'comment') ||
    (action === 'retire_rule' && report.target_type === 'rule') ||
    (action === 'archive_product' && report.target_type === 'product');

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (action !== 'none' && !actionApplies) {
      setError('The selected action does not match the report target type.');
      return;
    }
    resolve.mutate();
  }

  return (
    <Modal
      title="Review report"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="report-form"
            className="button primary"
            disabled={resolve.isPending}
          >
            {resolve.isPending ? 'Saving…' : 'Resolve report'}
          </button>
        </>
      }
    >
      <div className="detail-block">
        <p>
          <strong>{titleCase(report.reason)}</strong>
        </p>
        <p>{report.details ?? 'No additional details provided.'}</p>
        <p className="muted small">
          Target: {titleCase(report.target_type)} <code>{report.target_id}</code>
        </p>
      </div>
      <form id="report-form" className="form-stack" onSubmit={submit}>
        {error ? (
          <div className="notice error" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          Outcome
          <select
            value={status}
            onChange={(event) =>
              setStatus(event.target.value as Exclude<ReportStatus, 'open'>)
            }
          >
            <option value="investigating">Investigating</option>
            <option value="resolved">Resolved</option>
            <option value="dismissed">Dismissed</option>
          </select>
        </label>
        <label>
          Moderation action
          <select
            value={action}
            onChange={(event) =>
              setAction(
                event.target.value as
                  | 'none'
                  | 'hide_comment'
                  | 'retire_rule'
                  | 'archive_product',
              )
            }
          >
            <option value="none">No content action</option>
            <option value="hide_comment" disabled={report.target_type !== 'comment'}>
              Hide reported comment
            </option>
            <option value="retire_rule" disabled={report.target_type !== 'rule'}>
              Retire reported rule
            </option>
            <option value="archive_product" disabled={report.target_type !== 'product'}>
              Archive reported product
            </option>
          </select>
        </label>
        <label>
          Resolution note
          <textarea
            rows={3}
            maxLength={2000}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </label>
        <p className="muted small">
          Resolving a report awards reputation points to the reporter once. Content actions cannot
          be undone from this dialog.
        </p>
      </form>
    </Modal>
  );
}
