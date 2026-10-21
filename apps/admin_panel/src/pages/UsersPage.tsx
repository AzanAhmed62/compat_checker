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
import { listUsers, setUserRole, setUserStatus } from '../lib/api';
import { formatDate } from '../lib/format';
import { roleLabel } from '../lib/permissions';
import type { AccountStatus, Role, UserRow } from '../lib/types';

const roles: Role[] = ['member', 'contributor', 'trusted', 'admin'];
const statuses: AccountStatus[] = ['active', 'suspended', 'banned'];

export function UsersPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [action, setAction] = useState<{ user: UserRow; mode: 'role' | 'status' } | null>(null);

  const users = useQuery({
    queryKey: ['users', page, appliedSearch, role, status],
    queryFn: () => listUsers({ page, search: appliedSearch, role, status }),
  });

  async function completed() {
    setAction(null);
    await queryClient.invalidateQueries({ queryKey: ['users'] });
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
        title="Users and roles"
        description="Role and account-status changes require a reason, are audit logged, and notify the user. The final active administrator cannot be removed."
      />
      <form className="filter-bar" onSubmit={submitSearch} role="search">
        <label>
          Search
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Name, username, or email"
          />
        </label>
        <label>
          Role
          <select
            value={role}
            onChange={(event) => {
              setRole(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All roles</option>
            {roles.map((value) => (
              <option key={value} value={value}>
                {roleLabel(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Status
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {statuses.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
            <option value="deletion_pending">Deletion pending</option>
            <option value="deleted">Deleted</option>
          </select>
        </label>
        <button type="submit" className="button secondary">
          Apply
        </button>
      </form>

      {users.isLoading ? <LoadingPanel label="Loading users…" /> : null}
      {users.error ? <ErrorPanel error={users.error} retry={() => void users.refetch()} /> : null}
      {users.data && users.data.items.length === 0 ? (
        <EmptyPanel>No users match these filters.</EmptyPanel>
      ) : null}
      {users.data && users.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">User</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Reputation</th>
                  <th scope="col">Approved</th>
                  <th scope="col">Joined</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.data.items.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <strong>{user.display_name}</strong>
                      <div className="muted small">
                        @{user.username} · {user.email}
                      </div>
                    </td>
                    <td>
                      <Badge value={user.role} />
                    </td>
                    <td>
                      <Badge value={user.account_status} />
                    </td>
                    <td>{user.reputation_points}</td>
                    <td>{user.approved_contributions}</td>
                    <td>{formatDate(user.created_at)}</td>
                    <td>
                      <div className="row-actions">
                        <button
                          type="button"
                          className="button small secondary"
                          onClick={() => setAction({ user, mode: 'role' })}
                        >
                          Role
                        </button>
                        <button
                          type="button"
                          className="button small secondary"
                          onClick={() => setAction({ user, mode: 'status' })}
                        >
                          Status
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={users.data.limit}
            total={users.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {action ? (
        <UserAction
          user={action.user}
          mode={action.mode}
          onClose={() => setAction(null)}
          onDone={() => void completed()}
        />
      ) : null}
    </>
  );
}

function UserAction({
  user,
  mode,
  onClose,
  onDone,
}: {
  user: UserRow;
  mode: 'role' | 'status';
  onClose(): void;
  onDone(): void;
}) {
  const [value, setValue] = useState<string>(mode === 'role' ? user.role : user.account_status);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: async () => {
      if (reason.trim().length < 5) {
        throw new Error('A reason of at least 5 characters is required');
      }
      if (mode === 'role') {
        await setUserRole({ userId: user.id, role: value, reason });
      } else {
        await setUserStatus({ userId: user.id, status: value, reason });
      }
    },
    onSuccess: onDone,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (mode === 'status' && value !== 'active') {
      if (!window.confirm(`This will ${value === 'banned' ? 'ban' : value === 'suspended' ? 'suspend' : value} ${user.display_name}. Continue?`)) {
        return;
      }
    }
    mutation.mutate();
  }

  return (
    <Modal
      title={mode === 'role' ? 'Change user role' : 'Change account status'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="user-action-form"
            className="button primary"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? 'Saving…' : 'Apply change'}
          </button>
        </>
      }
    >
      <p>
        <strong>{user.display_name}</strong> <span className="muted">@{user.username}</span>
      </p>
      <form id="user-action-form" className="form-stack" onSubmit={submit}>
        {error ? (
          <div className="notice error" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          {mode === 'role' ? 'New role' : 'New status'}
          <select value={value} onChange={(event) => setValue(event.target.value)}>
            {mode === 'role'
              ? roles.map((roleValue) => (
                  <option key={roleValue} value={roleValue}>
                    {roleLabel(roleValue)}
                  </option>
                ))
              : statuses.map((statusValue) => (
                  <option key={statusValue} value={statusValue}>
                    {statusValue}
                  </option>
                ))}
          </select>
        </label>
        <label>
          Reason
          <textarea
            rows={3}
            maxLength={1000}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="This reason is stored in the audit log and moderation note."
          />
        </label>
        <p className="notice warning">
          {mode === 'role'
            ? 'Administrators can manage the catalog, users, and rollback. Trusted contributors can moderate queues but cannot perform administrator-only actions.'
            : 'Suspension and banning are enforced by the database and Supabase Auth. Banned sessions are rejected by server-side checks.'}
        </p>
      </form>
    </Modal>
  );
}
