import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { errorMessage } from '../lib/errors';
import { configurationError, requireClient } from '../lib/supabase';

function resultMessage(data: unknown, fallback: string): string {
  if (data && typeof data === 'object') {
    const message = (data as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim()) return message;
  }
  return fallback;
}

export function DeleteAccountPage() {
  const { session, me, loading, signIn, signOut, refresh } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);

  useEffect(() => {
    document.title = 'Delete your account - Compat Checker';
  }, []);

  const pending = me?.account_status === 'deletion_pending';

  async function handleSignIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setStatus(null);
    setBusy(true);
    try {
      await signIn(email.trim(), password);
      setPassword('');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleRequestDeletion() {
    if (!confirmed) return;
    setError(null);
    setStatus(null);
    setBusy(true);
    try {
      const { data, error: rpcError } = await requireClient().rpc('request_account_deletion');
      if (rpcError) throw rpcError;
      setStatus(resultMessage(data, 'Your account is scheduled for deletion.'));
      setConfirmed(false);
      await refresh();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleCancelDeletion() {
    setError(null);
    setStatus(null);
    setBusy(true);
    try {
      const { error: rpcError } = await requireClient().rpc('cancel_account_deletion');
      if (rpcError) throw rpcError;
      setStatus('Your account is active again. The pending deletion was cancelled.');
      await refresh();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  if (configurationError) {
    return (
      <main className="public-page">
        <article className="public-document">
          <h1>Account deletion unavailable</h1>
          <p>{configurationError}</p>
          <p className="muted">
            Account deletion is unavailable until this deployment has its public Supabase
            configuration. No service-role key is ever sent to a browser.
          </p>
          <Link to="/privacy">Privacy policy</Link>
        </article>
      </main>
    );
  }

  return (
    <main className="public-page">
      <article className="public-document">
        <p className="eyebrow">Compat Checker</p>
        <h1>Delete your account</h1>
        <p>
          You can request deletion without contacting support. Your profile is hidden immediately
          and the account is permanently purged after 30 days. You can cancel a pending deletion by
          signing in here before the purge runs.
        </p>
        <p className="notice warning" role="note">
          The purge removes the account, profile, and user-owned records. Some published catalog or
          guide content may remain without your account attribution. Limited security or moderation
          records may be retained as described in the <Link to="/privacy">privacy policy</Link>.
        </p>

        {error ? (
          <p className="notice error" role="alert">
            {error}
          </p>
        ) : null}
        {status ? (
          <p className="notice success" role="status" aria-live="polite">
            {status}
          </p>
        ) : null}

        {loading ? (
          <p role="status" aria-live="polite">
            Checking your session...
          </p>
        ) : !session ? (
          <>
            <h2>Sign in to continue</h2>
            <p className="muted">
              Sign in with the account you want to delete. If you no longer have access to the
              account email, use the support contact in the privacy policy.
            </p>
            <form className="form-stack" onSubmit={(event) => void handleSignIn(event)}>
              <label>
                Email
                <input
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </label>
              <button className="button primary" type="submit" disabled={busy}>
                {busy ? 'Signing in...' : 'Sign in'}
              </button>
            </form>
          </>
        ) : (
          <>
            <h2>Signed in</h2>
            <p>
              Account: <strong>{session.user.email ?? 'email unavailable'}</strong>
            </p>
            {pending ? (
              <>
                <p className="notice warning" role="status">
                  This account is already scheduled for deletion
                  {me?.deletion_requested_at
                    ? ` (requested ${new Date(me.deletion_requested_at).toLocaleString()})`
                    : ''}
                  .
                </p>
                <button
                  className="button primary"
                  type="button"
                  disabled={busy}
                  onClick={() => void handleCancelDeletion()}
                >
                  {busy ? 'Working...' : 'Cancel pending deletion'}
                </button>
              </>
            ) : (
              <>
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={confirmed}
                    onChange={(event) => setConfirmed(event.target.checked)}
                  />
                  I understand that some published catalog or guide content may remain without my
                  account attribution, and that the account will be purged after 30 days.
                </label>
                <div className="row-actions">
                  <button
                    className="button danger"
                    type="button"
                    disabled={busy || !confirmed}
                    onClick={() => void handleRequestDeletion()}
                  >
                    {busy ? 'Requesting...' : 'Request account deletion'}
                  </button>
                  <button
                    className="button secondary"
                    type="button"
                    disabled={busy}
                    onClick={() => void signOut()}
                  >
                    Sign out
                  </button>
                </div>
              </>
            )}
            {pending ? (
              <button
                className="button ghost"
                type="button"
                disabled={busy}
                onClick={() => void signOut()}
              >
                Sign out
              </button>
            ) : null}
          </>
        )}

        <p>
          <Link to="/privacy">Privacy policy</Link>
        </p>
      </article>
    </main>
  );
}
