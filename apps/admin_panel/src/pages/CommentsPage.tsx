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
import { listComments, moderateComment } from '../lib/api';
import { formatDate, truncate } from '../lib/format';
import type { CommentRow } from '../lib/types';

export function CommentsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('visible');
  const [searchText, setSearchText] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [selected, setSelected] = useState<CommentRow | null>(null);

  const comments = useQuery({
    queryKey: ['comments', page, status, appliedSearch],
    queryFn: () => listComments({ page, status, query: appliedSearch }),
  });

  async function completed() {
    setSelected(null);
    await queryClient.invalidateQueries({ queryKey: ['comments'] });
    await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedSearch(searchText.trim());
  }

  return (
    <>
      <PageHeader
        title="Comments"
        description="Moderate user-generated comments. Hiding retains the row for audit; deleting keeps the row but removes it from public projections."
        actions={
          <select
            aria-label="Filter comment status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option value="visible">Visible</option>
            <option value="hidden">Hidden</option>
            <option value="deleted">Deleted</option>
          </select>
        }
      />
      <form className="filter-bar" onSubmit={submitSearch} role="search">
        <label>
          Body contains
          <input value={searchText} onChange={(event) => setSearchText(event.target.value)} />
        </label>
        <button type="submit" className="button secondary">
          Search
        </button>
      </form>

      {comments.isLoading ? <LoadingPanel label="Loading comments…" /> : null}
      {comments.error ? (
        <ErrorPanel error={comments.error} retry={() => void comments.refetch()} />
      ) : null}
      {comments.data && comments.data.items.length === 0 ? (
        <EmptyPanel>No comments match these filters.</EmptyPanel>
      ) : null}
      {comments.data && comments.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Comment</th>
                  <th scope="col">Author</th>
                  <th scope="col">Target</th>
                  <th scope="col">Status</th>
                  <th scope="col">Created</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {comments.data.items.map((comment) => (
                  <tr key={comment.id}>
                    <td>
                      <p>{truncate(comment.body, 160)}</p>
                      {comment.parent_id ? (
                        <p className="muted small">Reply to {comment.parent_id}</p>
                      ) : null}
                    </td>
                    <td>{comment.author_name ?? 'Deleted user'}</td>
                    <td>
                      {comment.target_type}
                      <div className="muted small">
                        <code>{comment.target_id}</code>
                      </div>
                    </td>
                    <td>
                      <Badge value={comment.status} />
                    </td>
                    <td>{formatDate(comment.created_at)}</td>
                    <td>
                      <button
                        type="button"
                        className="button small secondary"
                        onClick={() => setSelected(comment)}
                      >
                        Moderate
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={comments.data.limit}
            total={comments.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {selected ? (
        <ModerateComment
          comment={selected}
          onClose={() => setSelected(null)}
          onDone={() => void completed()}
        />
      ) : null}
    </>
  );
}

function ModerateComment({
  comment,
  onClose,
  onDone,
}: {
  comment: CommentRow;
  onClose(): void;
  onDone(): void;
}) {
  const [status, setStatus] = useState<'visible' | 'hidden' | 'deleted'>(
    comment.status === 'deleted' ? 'deleted' : comment.status,
  );
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const moderate = useMutation({
    mutationFn: () => moderateComment({ id: comment.id, status, reason }),
    onSuccess: onDone,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (reason.trim().length < 3) {
      setError('A reason of at least 3 characters is required.');
      return;
    }
    moderate.mutate();
  }

  return (
    <Modal
      title="Moderate comment"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="comment-form"
            className="button primary"
            disabled={moderate.isPending}
          >
            {moderate.isPending ? 'Saving…' : 'Apply moderation'}
          </button>
        </>
      }
    >
      <blockquote className="comment-quote">{comment.body}</blockquote>
      <p className="muted small">
        By {comment.author_name ?? 'deleted user'} on {formatDate(comment.created_at)}
      </p>
      <form id="comment-form" className="form-stack" onSubmit={submit}>
        {error ? (
          <div className="notice error" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          New status
          <select
            value={status}
            onChange={(event) =>
              setStatus(event.target.value as 'visible' | 'hidden' | 'deleted')
            }
          >
            <option value="visible">Visible</option>
            <option value="hidden">Hidden</option>
            <option value="deleted">Deleted</option>
          </select>
        </label>
        <label>
          Reason
          <textarea
            rows={3}
            maxLength={1000}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        <p className="muted small">
          The author receives a notification unless the comment is restored to visible.
        </p>
      </form>
    </Modal>
  );
}
