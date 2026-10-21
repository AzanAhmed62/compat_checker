import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { canModerate } from '../lib/permissions';
import { configurationError } from '../lib/supabase';

export function LoginPage() {
  const { session, me, signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (configurationError) {
    return (
      <main className="auth-screen">
        <section className="auth-card">
          <h1>Configuration required</h1>
          <p>{configurationError}</p>
          <p className="muted">
            The console never accepts a service-role key. Only the public anon key belongs in a
            browser bundle.
          </p>
        </section>
      </main>
    );
  }

  if (session && me && canModerate(me.role, me.account_status)) {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signIn(email, password);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Sign-in failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-screen">
      <section className="auth-card">
        <div className="brand auth-brand">
          <span className="brand-mark" aria-hidden="true">
            CC
          </span>
          <div>
            <strong>Compat Checker</strong>
            <span className="brand-subtitle">Administration</span>
          </div>
        </div>
        <h1>Sign in</h1>
        <p className="muted">
          This area is restricted to trusted contributors and administrators. Public catalog
          browsing does not require an account.
        </p>
        <form onSubmit={(event) => void handleSubmit(event)} className="form-stack">
          <label>
            Email
            <input
              type="email"
              autoComplete="username"
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
              minLength={8}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error ? (
            <div className="notice error" role="alert">
              {error}
            </div>
          ) : null}
          <button type="submit" className="button primary" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="muted small">
          Never use production administrator credentials in a local database, and never place a
          service-role key in this app.
        </p>
      </section>
    </main>
  );
}
