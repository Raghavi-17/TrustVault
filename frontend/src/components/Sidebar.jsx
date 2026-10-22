import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LayoutDashboard, FileText, UploadCloud, Users, ShieldAlert, KeyRound } from 'lucide-react';

export const Sidebar = () => {
  const { user, isAdmin, isOwner } = useAuth();

  const navItems = [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/documents', label: 'Documents', icon: FileText },
    ...((isOwner || isAdmin) ? [{ to: '/upload', label: 'Upload Document', icon: UploadCloud }] : []),
    ...(isAdmin ? [
      { to: '/admin', label: 'User Management', icon: Users },
      { to: '/audit', label: 'Audit & Integrity', icon: ShieldAlert },
    ] : []),
  ];

  return (
    <aside
      style={{
        width: '240px',
        backgroundColor: 'var(--bg-sidebar)',
        borderRight: '1px solid var(--border-color)',
        display: 'flex',
        flexDirection: 'column',
        padding: '1.5rem 1rem',
        gap: '0.4rem',
        transition: 'var(--transition)',
      }}
    >
      <div style={{ padding: '0 0.5rem 1rem 0.5rem', borderBottom: '1px solid var(--border-color)', marginBottom: '0.75rem' }}>
        <span style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--text-muted)' }}>
          Navigation
        </span>
      </div>

      <nav style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              style={({ isActive }) => ({
                display: 'flex',
                alignItems: 'center',
                gap: '0.75rem',
                padding: '0.65rem 0.85rem',
                borderRadius: 'var(--radius-sm)',
                fontSize: '0.88rem',
                fontWeight: isActive ? 600 : 500,
                textDecoration: 'none',
                color: isActive ? 'var(--brand-primary)' : 'var(--text-secondary)',
                backgroundColor: isActive ? 'var(--bg-surface)' : 'transparent',
                transition: 'var(--transition)',
                borderLeft: isActive ? '3px solid var(--brand-primary)' : '3px solid transparent',
              })}
            >
              <Icon size={18} />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>

      <div style={{ marginTop: 'auto', padding: '1rem 0.5rem 0 0.5rem', borderTop: '1px solid var(--border-color)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: 'var(--status-success)' }} />
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Audit Chain: Active
          </span>
        </div>
      </div>
    </aside>
  );
};
