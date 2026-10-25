import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNavigation } from '../context/NavigationContext';
import { 
  LayoutDashboard, FileText, UploadCloud, Users, ShieldAlert, 
  ChevronLeft, ChevronRight, X, ShieldCheck 
} from 'lucide-react';

export const Sidebar = () => {
  const { user, isAdmin, isOwner } = useAuth();
  const { isCollapsed, toggleCollapse, isMobileOpen, closeMobile } = useNavigation();

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
    <>
      {/* =========================================================================
          1. DESKTOP SIDEBAR (Collapsible, persisted preference)
          ========================================================================= */}
      <aside
        className={`sidebar-desktop ${isCollapsed ? 'collapsed' : 'expanded'}`}
        style={{
          width: isCollapsed ? '68px' : '240px',
          backgroundColor: 'var(--bg-sidebar)',
          borderRight: '1px solid var(--border-nav)',
          display: 'flex',
          flexDirection: 'column',
          padding: isCollapsed ? '1.25rem 0.5rem' : '1.25rem 0.85rem',
          gap: '0.4rem',
          transition: 'width 0.22s cubic-bezier(0.4, 0, 0.2, 1), padding 0.22s ease',
          flexShrink: 0,
          position: 'relative',
        }}
      >
        {/* Header: Navigation label & Collapse / Expand toggle button */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: isCollapsed ? 'center' : 'space-between',
            padding: isCollapsed ? '0 0 0.85rem 0' : '0 0.35rem 0.85rem 0.35rem',
            borderBottom: '1px solid var(--border-nav)',
            marginBottom: '0.65rem',
            minHeight: '34px',
          }}
        >
          {!isCollapsed && (
            <span
              style={{
                fontSize: '0.72rem',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                color: 'var(--sidebar-text-muted)',
              }}
            >
              Navigation
            </span>
          )}
          <button
            onClick={toggleCollapse}
            aria-label={isCollapsed ? 'Expand sidebar navigation' : 'Collapse sidebar navigation'}
            title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className="sidebar-toggle-btn"
            style={{
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border-nav)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--sidebar-text)',
              width: '28px',
              height: '28px',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              transition: 'var(--transition)',
              padding: 0,
            }}
          >
            {isCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        </div>

        {/* Navigation list */}
        <nav
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '0.35rem',
          }}
        >
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => `sidebar-nav-item ${isActive ? 'active' : ''} ${isCollapsed ? 'collapsed' : ''}`}
                title={isCollapsed ? item.label : undefined}
                style={({ isActive }) => ({
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: isCollapsed ? 'center' : 'flex-start',
                  gap: isCollapsed ? 0 : '0.75rem',
                  padding: isCollapsed ? '0.7rem 0' : '0.65rem 0.85rem',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.88rem',
                  fontWeight: isActive ? 600 : 500,
                  textDecoration: 'none',
                  color: isActive ? 'var(--brand-highlight)' : 'var(--sidebar-text)',
                  backgroundColor: isActive ? 'var(--sidebar-active-bg)' : 'transparent',
                  transition: 'var(--transition)',
                  borderLeft: isCollapsed ? 'none' : (isActive ? '3px solid var(--brand-highlight)' : '3px solid transparent'),
                  boxShadow: (isCollapsed && isActive) ? 'inset 3px 0 0 var(--brand-highlight)' : 'none',
                  position: 'relative',
                })}
              >
                <Icon size={19} style={{ flexShrink: 0 }} />
                {!isCollapsed && <span>{item.label}</span>}
                {isCollapsed && (
                  <span className="sidebar-tooltip" role="tooltip">
                    {item.label}
                  </span>
                )}
              </NavLink>
            );
          })}
        </nav>

        {/* Desktop Footer: Audit Trail Security Indicator */}
        <div
          style={{
            marginTop: 'auto',
            padding: isCollapsed ? '0.85rem 0 0 0' : '0.85rem 0.35rem 0 0.35rem',
            borderTop: '1px solid var(--border-nav)',
            display: 'flex',
            justifyContent: isCollapsed ? 'center' : 'flex-start',
          }}
        >
          <div
            title="SHA-256 Chained Audit Trail: Active"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            <div
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: 'var(--status-success)',
                boxShadow: '0 0 6px rgba(78, 189, 118, 0.6)',
                flexShrink: 0,
              }}
            />
            {!isCollapsed && (
              <span style={{ fontSize: '0.75rem', color: 'var(--sidebar-text-muted)' }}>
                Audit Chain: Active
              </span>
            )}
          </div>
        </div>
      </aside>

      {/* =========================================================================
          2. MOBILE DRAWER & BACKDROP OVERLAY
          ========================================================================= */}
      {isMobileOpen && (
        <div
          className="sidebar-mobile-backdrop"
          onClick={closeMobile}
          aria-hidden="true"
        />
      )}

      <aside
        className={`sidebar-mobile-drawer ${isMobileOpen ? 'open' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label="Mobile navigation drawer"
      >
        <div className="sidebar-mobile-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <div
              style={{
                background: 'var(--brand-primary)',
                color: '#FFFFFF',
                padding: '0.4rem',
                borderRadius: 'var(--radius-sm)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <ShieldCheck size={18} />
            </div>
            <span style={{ fontSize: '1.15rem', fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--nav-text)' }}>
              Trust<span style={{ color: 'var(--brand-highlight)' }}>Vault</span>
            </span>
          </div>

          <button
            onClick={closeMobile}
            aria-label="Close navigation drawer"
            className="sidebar-mobile-close-btn"
          >
            <X size={18} />
          </button>
        </div>

        <nav style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', padding: '1.25rem 1rem' }}>
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={closeMobile}
                style={({ isActive }) => ({
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.85rem',
                  padding: '0.75rem 1rem',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.95rem',
                  fontWeight: isActive ? 600 : 500,
                  textDecoration: 'none',
                  color: isActive ? 'var(--brand-highlight)' : 'var(--sidebar-text)',
                  backgroundColor: isActive ? 'var(--sidebar-active-bg)' : 'transparent',
                  borderLeft: isActive ? '3px solid var(--brand-highlight)' : '3px solid transparent',
                  transition: 'var(--transition)',
                })}
              >
                <Icon size={20} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        <div style={{ marginTop: 'auto', padding: '1.25rem 1rem', borderTop: '1px solid var(--border-nav)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: 'var(--status-success)',
                boxShadow: '0 0 6px rgba(78, 189, 118, 0.6)',
              }}
            />
            <span style={{ fontSize: '0.78rem', color: 'var(--sidebar-text-muted)' }}>
              Audit Chain: Active
            </span>
          </div>
        </div>
      </aside>
    </>
  );
};
