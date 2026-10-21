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
import { listContributions, reviewContribution } from '../lib/api';
import { formatDate, prettyJson, titleCase } from '../lib/format';
import type {
  Contribution,
  ContributionKind,
  ContributionStatus,
} from '../lib/types';

const kinds: ContributionKind[] = [
  'new_product',
  'product_edit',
  'new_rule',
  'evidence',
  'guide',
];
const statuses: ContributionStatus[] = [
  'pending',
  'needs_changes',
  'approved',
  'rejected',
  'withdrawn',
];

export function ContributionsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<ContributionStatus | ''>('pending');
  const [kind, setKind] = useState<ContributionKind | ''>('');
  const [selected, setSelected] = useState<Contribution | null>(null);

  const contributions = useQuery({
    queryKey: ['contributions', page, status, kind],
    queryFn: () => listContributions({ page, status, kind }),
  });

  async function completed() {
    setSelected(null);
    await queryClient.invalidateQueries({ queryKey: ['contributions'] });
    await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    await queryClient.invalidateQueries({ queryKey: ['products'] });
    await queryClient.invalidateQueries({ queryKey: ['rules'] });
    await queryClient.invalidateQueries({ queryKey: ['guides'] });
  }

  return (
    <>
      <PageHeader
        title="Contributions"
        description="Approving applies the contribution atomically and awards reputation once. Contributors cannot review their own work."
        actions={
          <div className="row-actions">
            <select
              aria-label="Filter contribution kind"
              value={kind}
              onChange={(event) => {
                setKind(event.target.value as ContributionKind | '');
                setPage(0);
              }}
            >
              <option value="">All kinds</option>
              {kinds.map((value) => (
                <option key={value} value={value}>
                  {titleCase(value)}
                </option>
              ))}
            </select>
            <select
              aria-label="Filter contribution status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as ContributionStatus | '');
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              {statuses.map((value) => (
                <option key={value} value={value}>
                  {titleCase(value)}
                </option>
              ))}
            </select>
          </div>
        }
      />

      {contributions.isLoading ? <LoadingPanel label="Loading contributions…" /> : null}
      {contributions.error ? (
        <ErrorPanel error={contributions.error} retry={() => void contributions.refetch()} />
      ) : null}
      {contributions.data && contributions.data.items.length === 0 ? (
        <EmptyPanel>No contributions match these filters.</EmptyPanel>
      ) : null}
      {contributions.data && contributions.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Kind</th>
                  <th scope="col">Payload</th>
                  <th scope="col">Contributor</th>
                  <th scope="col">Status</th>
                  <th scope="col">Submitted</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {contributions.data.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <Badge value={item.kind} />
                    </td>
                    <td>
                      <strong>
                        {String(
                          item.payload.name ??
                            item.payload.title ??
                            item.payload.summary ??
                            'Structured payload',
                        )}
                      </strong>
                      <details className="inline-details">
                        <summary>View payload</summary>
                        <pre>{prettyJson(item.payload)}</pre>
                      </details>
                    </td>
                    <td>{item.contributor_name ?? 'Deleted user'}</td>
                    <td>
                      <Badge value={item.status} />
                      {item.review_note ? (
                        <div className="muted small">{item.review_note}</div>
                      ) : null}
                    </td>
                    <td>{formatDate(item.created_at)}</td>
                    <td>
                      {item.status === 'pending' || item.status === 'needs_changes' ? (
                        <button
                          type="button"
                          className="button small primary"
                          onClick={() => setSelected(item)}
                        >
                          Review
                        </button>
                      ) : (
                        <span className="muted small">
                          {item.reviewer_name ? `by ${item.reviewer_name}` : 'Reviewed'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={contributions.data.limit}
            total={contributions.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {selected ? (
        <ReviewContribution
          contribution={selected}
          onClose={() => setSelected(null)}
          onDone={() => void completed()}
        />
      ) : null}
    </>
  );
}

function ReviewContribution({
  contribution,
  onClose,
  onDone,
}: {
  contribution: Contribution;
  onClose(): void;
  onDone(): void;
}) {
  const [decision, setDecision] = useState<'approved' | 'rejected' | 'needs_changes'>('approved');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const review = useMutation({
    mutationFn: () =>
      reviewContribution({ id: contribution.id, decision, note }),
    onSuccess: onDone,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (decision !== 'approved' && note.trim().length < 5) {
      setError('Rejections and change requests require a note of at least 5 characters.');
      return;
    }
    if (!window.confirm(`Apply “${decision.replace('_', ' ')}” to this contribution?`)) {
      return;
    }
    review.mutate();
  }

  return (
    <Modal
      title={`Review ${titleCase(contribution.kind)}`}
      onClose={onClose}
      wide
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="review-form"
            className="button primary"
            disabled={review.isPending}
          >
            {review.isPending ? 'Applying…' : 'Apply decision'}
          </button>
        </>
      }
    >
      <div className="detail-block">
        <p>
          Submitted by <strong>{contribution.contributor_name ?? 'deleted user'}</strong> on{' '}
          {formatDate(contribution.created_at)}.
        </p>
        <pre>{prettyJson(contribution.payload)}</pre>
      </div>
      <form id="review-form" className="form-stack" onSubmit={submit}>
        {error ? (
          <div className="notice error" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          Decision
          <select
            value={decision}
            onChange={(event) =>
              setDecision(event.target.value as 'approved' | 'rejected' | 'needs_changes')
            }
          >
            <option value="approved">Approve and apply</option>
            <option value="rejected">Reject</option>
            <option value="needs_changes">Request changes</option>
          </select>
        </label>
        <label>
          Reviewer note
          <textarea
            rows={4}
            maxLength={2000}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder="Explain the decision. Required for rejection or requested changes."
          />
        </label>
        <p className="notice warning">
          Approval is irreversible from this screen. Product, rule, evidence, or guide rows may be
          created immediately, and reputation is awarded exactly once.
        </p>
      </form>
    </Modal>
  );
}
