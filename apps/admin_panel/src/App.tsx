import { Component, useEffect, type ErrorInfo, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './auth/AuthProvider';
import { Layout } from './components/Layout';
import { canAdmin, canModerate } from './lib/permissions';
import { configurationError } from './lib/supabase';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { CatalogPage } from './pages/CatalogPage';
import { RulesPage } from './pages/RulesPage';
import { ReportsPage } from './pages/ReportsPage';
import { ContributionsPage } from './pages/ContributionsPage';
import { CommentsPage } from './pages/CommentsPage';
import { RequestsPage } from './pages/RequestsPage';
import { UsersPage } from './pages/UsersPage';
import { GuidesPage } from './pages/GuidesPage';
import { MediaPage } from './pages/MediaPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { AuditPage } from './pages/AuditPage';
import { ImportsPage } from './pages/ImportsPage';
import { NotFoundPage } from './pages/NotFoundPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 20_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Admin console render failed', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <main className="auth-screen">
          <section className="auth-card">
            <h1>Something went wrong</h1>
            <p>The console hit an unexpected rendering error. Reload to try again.</p>
            <button
              type="button"
              className="button primary"
              onClick={() => window.location.assign('/')}
            >
              Reload
            </button>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}

function Screen({ children }: { children: ReactNode }) {
  return (
    <main className="auth-screen">
      <section className="auth-card">{children}</section>
    </main>
  );
}

function AccessProblem({ message }: { message: string }) {
  const { signOut } = useAuth();
  return (
    <Screen>
      <h1>Access unavailable</h1>
      <p role="alert">{message}</p>
      <button type="button" className="button secondary" onClick={() => void signOut()}>
        Sign out
      </button>
    </Screen>
  );
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  if (!canAdmin(me?.role, me?.account_status)) {
    return (
      <Screen>
        <h1>Administrator access required</h1>
        <p>This area is limited to administrators. Moderation queues remain available to you.</p>
      </Screen>
    );
  }
  return <>{children}</>;
}

function ProtectedApp() {
  const { session, me, loading, error } = useAuth();

  useEffect(() => {
    document.title = 'Compat Checker Admin';
  }, []);

  if (configurationError) return <Navigate to="/login" replace />;
  if (loading) {
    return (
      <Screen>
        <div role="status" aria-live="polite">
          Checking your session…
        </div>
      </Screen>
    );
  }
  if (!session) return <Navigate to="/login" replace />;
  if (error) return <AccessProblem message={error} />;
  if (!me) return <AccessProblem message="Your profile could not be loaded." />;
  if (!canModerate(me.role, me.account_status)) {
    return (
      <Screen>
        <h1>Administration access denied</h1>
        <p>
          Your account ({me.role}) does not have moderation privileges. Public browsing remains
          available in the mobile app.
        </p>
      </Screen>
    );
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route
          index
          element={
            <AdminOnly>
              <DashboardPage />
            </AdminOnly>
          }
        />
        <Route
          path="catalog"
          element={
            <AdminOnly>
              <CatalogPage />
            </AdminOnly>
          }
        />
        <Route
          path="rules"
          element={
            <AdminOnly>
              <RulesPage />
            </AdminOnly>
          }
        />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="contributions" element={<ContributionsPage />} />
        <Route path="comments" element={<CommentsPage />} />
        <Route path="requests" element={<RequestsPage />} />
        <Route
          path="users"
          element={
            <AdminOnly>
              <UsersPage />
            </AdminOnly>
          }
        />
        <Route
          path="guides"
          element={
            <AdminOnly>
              <GuidesPage />
            </AdminOnly>
          }
        />
        <Route
          path="media"
          element={
            <AdminOnly>
              <MediaPage />
            </AdminOnly>
          }
        />
        <Route
          path="analytics"
          element={
            <AdminOnly>
              <AnalyticsPage />
            </AdminOnly>
          }
        />
        <Route
          path="audit"
          element={
            <AdminOnly>
              <AuditPage />
            </AdminOnly>
          }
        />
        <Route
          path="imports"
          element={
            <AdminOnly>
              <ImportsPage />
            </AdminOnly>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/*" element={<ProtectedApp />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
