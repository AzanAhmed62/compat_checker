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
import { listProductOptions, listRequests, updateRequest } from '../lib/api';
import { formatDate, truncate } from '../lib/format';
import type { RequestRow, RequestStatus } from '../lib/types';

export function RequestsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<RequestStatus | ''>('open');
  const [search, setSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [selected, setSelected] = useState<RequestRow | null>(null);

  const requests = useQuery({
    queryKey: ['requests', page, status, appliedSearch],
    queryFn: () => listRequests({ page, status, query: appliedSearch }),
  });
  const products = useQuery({ queryKey: ['product-options'], queryFn: listProductOptions });

  async function completed() {
    setSelected(null);
    await queryClient.invalidateQueries({ queryKey: ['requests'] });
    await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedSearch(search.trim());
  }

  return (
    <>
      <PageHeader
        title="Product requests"
        description="Triage community requests. Marking a request fulfilled requires a published product."
        actions={
          <select
            aria-label="Filter request status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as RequestStatus | '');
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option value="open">Open</option>
            <option value="planned">Planned</option>
            <option value="fulfilled">Fulfilled</option>
            <option value="declined">Declined</option>
          </select>
        }
      />
      <form className="filter-bar" onSubmit={submitSearch} role="search">
        <label>
          Title contains
          <input value={search} onChange={(event) => setSearch(event.target.value)} />
        </label>
        <button type="submit" className="button secondary">
          Search
        </button>
      </form>

      {requests.isLoading ? <LoadingPanel label="Loading requests…" /> : null}
      {requests.error ? (
        <ErrorPanel error={requests.error} retry={() => void requests.refetch()} />
      ) : null}
      {requests.data && requests.data.items.length === 0 ? (
        <EmptyPanel>No requests match these filters.</EmptyPanel>
      ) : null}
      {requests.data && requests.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Request</th>
                  <th scope="col">Category</th>
                  <th scope="col">Votes</th>
                  <th scope="col">Status</th>
                  <th scope="col">Created</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {requests.data.items.map((request) => (
                  <tr key={request.id}>
                    <td>
                      <strong>{request.title}</strong>
                      <div className="muted small">{truncate(request.description, 120)}</div>
                      {request.is_anonymous ? (
                        <div className="muted small">Submitted anonymously publicly</div>
                      ) : null}
                    </td>
                    <td>{request.category_name ?? 'Uncategorised'}</td>
                    <td>{request.vote_count}</td>
                    <td>
                      <Badge value={request.status} />
                    </td>
                    <td>{formatDate(request.created_at)}</td>
                    <td>
                      <button
                        type="button"
                        className="button small secondary"
                        onClick={() => setSelected(request)}
                      >
                        Triage
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={requests.data.limit}
            total={requests.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {selected ? (
        <TriageRequest
          request={selected}
          products={(products.data ?? []).filter((product) => product.status === 'published')}
          onClose={() => setSelected(null)}
          onDone={() => void completed()}
        />
      ) : null}
    </>
  );
}

function TriageRequest({
  request,
  products,
  onClose,
  onDone,
}: {
  request: RequestRow;
  products: Array<{ id: string; name: string }>;
  onClose(): void;
  onDone(): void;
}) {
  const [status, setStatus] = useState<RequestStatus>(request.status);
  const [productId, setProductId] = useState(request.fulfilled_product_id ?? '');
  const [error, setError] = useState<string | null>(null);
  const update = useMutation({
    mutationFn: () =>
      updateRequest({
        id: request.id,
        status,
        fulfilledProductId: productId || null,
      }),
    onSuccess: onDone,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (status === 'fulfilled' && !productId) {
      setError('Choose the published product that fulfils this request.');
      return;
    }
    update.mutate();
  }

  return (
    <Modal
      title={request.title}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="request-form"
            className="button primary"
            disabled={update.isPending}
          >
            {update.isPending ? 'Saving…' : 'Update request'}
          </button>
        </>
      }
    >
      <p>{request.description}</p>
      <p className="muted small">
        {request.vote_count} votes · submitted {formatDate(request.created_at)}
      </p>
      <form id="request-form" className="form-stack" onSubmit={submit}>
        {error ? (
          <div className="notice error" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          Status
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as RequestStatus)}
          >
            <option value="open">Open</option>
            <option value="planned">Planned</option>
            <option value="fulfilled">Fulfilled</option>
            <option value="declined">Declined</option>
          </select>
        </label>
        <label>
          Fulfilling product
          <select value={productId} onChange={(event) => setProductId(event.target.value)}>
            <option value="">No product selected</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name}
              </option>
            ))}
          </select>
        </label>
        <p className="muted small">
          The requester receives a notification. An anonymous request does not expose the
          requester’s identity publicly, but administrators can see the linked account here for
          moderation.
        </p>
      </form>
    </Modal>
  );
}
