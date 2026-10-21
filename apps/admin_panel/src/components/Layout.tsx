import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { canAdmin } from '../lib/permissions';
import { roleLabel } from '../lib/permissions';

const adminLinks = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/catalog', label: 'Catalog' },
  { to: '/rules', label: 'Compatibility rules' },
  { to: '/contributions', label: 'Contributions' },
  { to: '/reports', label: 'Reports' },
  { to: '/comments', label: 'Comments' },
  { to: '/requests', label: 'Requests' },
  { to: '/users', label: 'Users & roles' },
  { to: '/guides', label: 'Guides' },
  { to: '/media', label: 'Media' },
  { to: '/analytics', label: 'Analytics' },
  { to: '/audit', label: 'Audit log' },
  { to: '/imports', label: 'CSV tools' },
] as const;

const moderatorLinks = [
  { to: '/contributions', label: 'Contributions' },
  { to: '/reports', label: 'Reports' },
  { to: '/comments', label: 'Comments' },
  { to: '/requests', label: 'Requests' },
] as const;

export function Layout() {
  const { me, signOut } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const admin = canAdmin(me?.role, me?.account_status);
  const links = admin ? adminLinks : moderatorLinks;
  const environment = import.meta.env.VITE_ENVIRONMENT_LABEL ?? 'Local / unlabelled';

  async function handleSignOut() {
    await signOut();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <aside className={`sidebar${open ? ' sidebar-open' : ''}`} aria-label="Primary">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            CC
          </span>
          <div>
            <strong>Compat Checker</strong>
            <span className="brand-subtitle">Admin console</span>
          </div>
        </div>
        <nav className="nav-list">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={'end' in link ? link.end : false}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
              onClick={() => setOpen(false)}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="environment">{environment}</span>
          <p className="muted small">
            Compatibility claims require published evidence. Changes are audit logged.
          </p>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <button
            type="button"
            className="button secondary menu-button"
            aria-expanded={open}
            aria-controls="sidebar"
            onClick={() => setOpen((value) => !value)}
          >
            Menu
          </button>
          <div className="topbar-user">
            <span>
              <strong>{me?.display_name}</strong>{' '}
              <span className="muted">@{me?.username}</span>
            </span>
            <span className="badge badge-role">{me ? roleLabel(me.role) : ''}</span>
            <button type="button" className="button ghost" onClick={() => void handleSignOut()}>
              Sign out
            </button>
          </div>
        </header>
        <main id="main-content" className="content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
