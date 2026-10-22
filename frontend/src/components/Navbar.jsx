import React from 'react';
import { useAuth } from '../context/AuthContext';
import { ThemeToggle } from './ThemeToggle';
import { Badge } from './Badge';
import { ShieldCheck, LogOut, User as UserIcon } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const getRoleBadgeVariant = (role) => {
    switch (role) {
      case 'ADMIN': return 'error';
      case 'OWNER': return 'success';
      case 'VIEWER': return 'info';
      default: return 'neutral';
    }
  };

  return (
    <header
      style={{
        height: '64px',
        backgroundColor: 'var(--bg-card)',
        borderBottom: '1px solid var(--border-color)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 2rem',
        position: 'sticky',
        top: 0,
        zIndex: 50,
        transition: 'var(--transition)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <div
          style={{
            background: 'var(--brand-primary)',
            color: '#FFFFFF',
            padding: '0.45rem',
            borderRadius: 'var(--radius-sm)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <ShieldCheck size={22} />
        </div>
        <div>
          <span style={{ fontSize: '1.2rem', fontWeight: 800, letterSpacing: '-0.03em', color: 'var(--text-primary)' }}>
            Trust<span style={{ color: 'var(--brand-primary)' }}>Vault</span>
          </span>
          <span style={{ marginLeft: '0.5rem', fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600 }}>
            v1.0.0
          </span>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
        <ThemeToggle />

        {user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', borderLeft: '1px solid var(--border-color)', paddingLeft: '1.25rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', lineHeight: '1.2' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {user.fullName || user.username}
                </span>
                <Badge variant={getRoleBadgeVariant(user.role)}>
                  {user.role}
                </Badge>
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                {user.email}
              </span>
            </div>

            <button
              onClick={handleLogout}
              className="btn btn-outline btn-sm"
              title="Sign out of TrustVault"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
            >
              <LogOut size={15} />
              <span>Logout</span>
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
