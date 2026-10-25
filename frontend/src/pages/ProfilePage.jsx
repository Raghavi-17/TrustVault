import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { Badge } from '../components/Badge';
import { ArrowLeft, Mail, Copy, Check } from 'lucide-react';

export const ProfilePage = () => {
  const { user } = useAuth();
  const [copied, setCopied] = useState(false);

  if (!user) {
    return (
      <div className="page-body">
        <p style={{ color: 'var(--text-secondary)' }}>Loading user profile...</p>
      </div>
    );
  }

  const getRoleBadgeVariant = (role) => {
    switch (role) {
      case 'ADMIN': return 'copper';
      case 'OWNER': return 'success';
      case 'VIEWER': return 'info';
      default: return 'neutral';
    }
  };

  const getUserInitials = (name) => {
    if (!name) return 'U';
    const clean = name.replace(/\(.*?\)/g, '').trim();
    const parts = clean.split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }
    return clean.slice(0, 2).toUpperCase();
  };

  const handleCopyAccountId = async () => {
    if (!user?.id) return;
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(user.id);
      } else {
        const textArea = document.createElement('textarea');
        textArea.value = user.id;
        textArea.style.position = 'fixed';
        textArea.style.opacity = '0';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.warn('Failed to copy account ID:', err);
    }
  };

  const statusLabel = user.status || (user.isActive ? 'ACTIVE' : 'PENDING');
  const isActive = statusLabel === 'ACTIVE';

  return (
    <div className="page-body" style={{ maxWidth: '800px', margin: '0 auto', padding: '2rem 1.5rem' }}>
      {/* 1. Top Navigation: Back Link to Dashboard */}
      <div style={{ marginBottom: '1.25rem' }}>
        <Link
          to="/dashboard"
          className="profile-back-link"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            color: 'var(--text-secondary)',
            fontSize: '0.85rem',
            fontWeight: 500,
            textDecoration: 'none',
            transition: 'var(--transition)',
            padding: '0.25rem 0',
          }}
        >
          <ArrowLeft size={16} />
          <span>Dashboard</span>
        </Link>
      </div>

      {/* 2. Refined Page Header */}
      <div style={{ marginBottom: '1.75rem' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: '0 0 0.35rem 0', letterSpacing: '-0.02em', color: 'var(--text-primary)' }}>
          My Profile
        </h1>
        <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
          Manage your personal credentials, cryptographic identity, and vault authorization.
        </p>
      </div>

      {/* 3. Streamlined Enterprise Profile Card */}
      <div
        className="card"
        style={{
          maxWidth: '540px',
          padding: '1.75rem',
          backgroundColor: 'var(--bg-card)',
          border: '1px solid var(--border-color)',
          borderRadius: 'var(--radius-md)',
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        {/* Profile Header: Avatar + Full Name + Email */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '1.25rem',
            paddingBottom: '1.5rem',
            borderBottom: '1px solid var(--border-color)',
          }}
        >
          <div
            style={{
              width: '60px',
              height: '60px',
              borderRadius: '50%',
              backgroundColor: 'var(--bg-elevated)',
              border: '2px solid var(--brand-highlight)',
              color: 'var(--brand-highlight)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: '1.3rem',
              flexShrink: 0,
              boxShadow: 'var(--shadow-sm)',
              overflow: 'hidden',
            }}
          >
            {user.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt={user.fullName || user.username}
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : (
              <span>{getUserInitials(user.fullName || user.username)}</span>
            )}
          </div>

          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap', marginBottom: '0.2rem' }}>
              <h2
                style={{
                  fontSize: '1.25rem',
                  fontWeight: 700,
                  margin: 0,
                  color: 'var(--text-primary)',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {user.fullName || user.username}
              </h2>
            </div>
            <p
              style={{
                margin: 0,
                fontSize: '0.84rem',
                color: 'var(--text-secondary)',
                display: 'flex',
                alignItems: 'center',
                gap: '0.4rem',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              <Mail size={13} style={{ flexShrink: 0, opacity: 0.8 }} />
              <span>{user.email}</span>
            </p>
          </div>
        </div>

        {/* Account Details Section */}
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {/* Row: Username */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '1rem 0',
              borderBottom: '1px solid var(--border-color)',
              gap: '1rem',
            }}
          >
            <span style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Username
            </span>
            <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-primary)' }}>
              @{user.username}
            </span>
          </div>

          {/* Row: Vault Role */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '1rem 0',
              borderBottom: '1px solid var(--border-color)',
              gap: '1rem',
            }}
          >
            <span style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Vault Role
            </span>
            <div>
              <Badge variant={getRoleBadgeVariant(user.role)}>
                {user.role}
              </Badge>
            </div>
          </div>

          {/* Row: Account Status */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '1rem 0',
              borderBottom: '1px solid var(--border-color)',
              gap: '1rem',
            }}
          >
            <span style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Account Status
            </span>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.45rem' }}>
              <div
                style={{
                  width: '8px',
                  height: '8px',
                  borderRadius: '50%',
                  backgroundColor: isActive ? 'var(--status-success)' : 'var(--status-warning)',
                  boxShadow: isActive ? '0 0 6px rgba(78, 189, 118, 0.45)' : 'none',
                }}
              />
              <span
                style={{
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  color: isActive ? 'var(--status-success)' : 'var(--status-warning)',
                }}
              >
                {statusLabel}
              </span>
            </div>
          </div>

          {/* Row: Vault Account ID */}
          {user.id && (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
                paddingTop: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  Vault Account ID
                </span>
                <button
                  type="button"
                  onClick={handleCopyAccountId}
                  className="btn btn-sm btn-nav"
                  aria-label={copied ? "Copied Vault Account ID" : "Copy Vault Account ID to clipboard"}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '0.35rem',
                    padding: '0.25rem 0.55rem',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border-color)',
                    backgroundColor: 'var(--bg-elevated)',
                    color: copied ? 'var(--status-success)' : 'var(--text-secondary)',
                    cursor: 'pointer',
                    transition: 'var(--transition)',
                  }}
                >
                  {copied ? (
                    <>
                      <Check size={13} style={{ color: 'var(--status-success)' }} />
                      <span>Copied!</span>
                    </>
                  ) : (
                    <>
                      <Copy size={13} />
                      <span>Copy</span>
                    </>
                  )}
                </button>
              </div>

              <div
                style={{
                  padding: '0.55rem 0.75rem',
                  backgroundColor: 'var(--bg-elevated)',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border-color)',
                  fontFamily: 'JetBrains Mono, monospace',
                  fontSize: '0.8rem',
                  color: 'var(--text-muted)',
                  wordBreak: 'break-all',
                  userSelect: 'all',
                }}
              >
                {user.id}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
