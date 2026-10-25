import React, { useState, useRef, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigation } from '../context/NavigationContext';
import { ThemeToggle } from './ThemeToggle';
import { Badge } from './Badge';
import { ShieldCheck, LogOut, User as UserIcon, Menu } from 'lucide-react';
import { useNavigate, Link } from 'react-router-dom';

export const Navbar = () => {
  const { user, logout } = useAuth();
  const { openMobile, isMobileOpen } = useNavigation();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);
  const navigate = useNavigate();

  // Close dropdown on outside click
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setDropdownOpen(false);
      }
    };
    if (dropdownOpen) {
      document.addEventListener('mousedown', handleOutsideClick);
      document.addEventListener('touchstart', handleOutsideClick);
    }
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('touchstart', handleOutsideClick);
    };
  }, [dropdownOpen]);

  // Close dropdown on Escape key
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && dropdownOpen) {
        setDropdownOpen(false);
      }
    };
    if (dropdownOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [dropdownOpen]);

  const handleLogout = async () => {
    setDropdownOpen(false);
    await logout();
    navigate('/login');
  };

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

  return (
    <header
      style={{
        height: '64px',
        backgroundColor: 'var(--bg-navbar)',
        borderBottom: '1px solid var(--border-nav)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 1.5rem',
        position: 'sticky',
        top: 0,
        zIndex: 50,
        transition: 'var(--transition)',
      }}
    >
      {/* Left side: Hamburger menu (mobile) + TrustVault branding */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <button
          onClick={openMobile}
          className="navbar-hamburger-btn"
          aria-label="Open navigation menu"
          aria-expanded={isMobileOpen}
          style={{
            alignItems: 'center',
            justifyContent: 'center',
            width: '36px',
            height: '36px',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border-nav)',
            backgroundColor: 'var(--btn-nav-bg)',
            color: 'var(--btn-nav-text)',
            cursor: 'pointer',
            transition: 'var(--transition)',
            padding: 0,
          }}
        >
          <Menu size={20} />
        </button>

        <div
          style={{
            background: 'var(--brand-primary)',
            color: '#FFFFFF',
            padding: '0.45rem',
            borderRadius: 'var(--radius-sm)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 'var(--shadow-sm)',
          }}
        >
          <ShieldCheck size={22} />
        </div>
        <span style={{ fontSize: '1.2rem', fontWeight: 800, letterSpacing: '-0.03em', color: 'var(--nav-text)' }}>
          Trust<span style={{ color: 'var(--brand-highlight)' }}>Vault</span>
        </span>
      </div>

      {/* Right side: Theme Toggle + User Profile Avatar Dropdown */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <ThemeToggle />

        {user && (
          <div style={{ position: 'relative' }} ref={dropdownRef}>
            <button
              onClick={() => setDropdownOpen((prev) => !prev)}
              aria-label="User account menu"
              aria-haspopup="true"
              aria-expanded={dropdownOpen}
              className="navbar-avatar-btn"
              title={`${user.fullName || user.username} (${user.role})`}
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '50%',
                backgroundColor: 'var(--bg-elevated)',
                border: dropdownOpen ? '2px solid var(--brand-highlight)' : '2px solid var(--border-color)',
                color: 'var(--brand-highlight)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: '0.86rem',
                cursor: 'pointer',
                transition: 'var(--transition)',
                boxShadow: dropdownOpen ? '0 0 0 3px var(--brand-focus)' : 'var(--shadow-sm)',
                padding: 0,
                outline: 'none',
              }}
            >
              {user.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt={user.fullName || user.username}
                  style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
                />
              ) : (
                <span>{getUserInitials(user.fullName || user.username)}</span>
              )}
            </button>

            {/* User Profile Dropdown Menu */}
            {dropdownOpen && (
              <div
                className="profile-dropdown-card"
                role="menu"
                aria-label="User account options"
                style={{
                  position: 'absolute',
                  top: 'calc(100% + 8px)',
                  right: 0,
                  width: '260px',
                  backgroundColor: 'var(--bg-card)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 'var(--radius-md)',
                  boxShadow: 'var(--shadow-lg)',
                  padding: '0.5rem 0',
                  zIndex: 100,
                  animation: 'dropdownEnter 0.16s ease-out',
                }}
              >
                {/* 1. Header: Name, Email, Role Badge */}
                <div
                  style={{
                    padding: '0.65rem 1rem 0.75rem 1rem',
                    borderBottom: '1px solid var(--border-color)',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '0.5rem',
                      marginBottom: '0.25rem',
                    }}
                  >
                    <span
                      style={{
                        fontWeight: 700,
                        fontSize: '0.92rem',
                        color: 'var(--text-primary)',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {user.fullName || user.username}
                    </span>
                    <Badge variant={getRoleBadgeVariant(user.role)}>
                      {user.role}
                    </Badge>
                  </div>
                  <div
                    style={{
                      fontSize: '0.78rem',
                      color: 'var(--text-secondary)',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {user.email}
                  </div>
                </div>

                {/* 2. Menu Links: My Profile */}
                <div style={{ padding: '0.35rem 0' }}>
                  <Link
                    to="/profile"
                    role="menuitem"
                    onClick={() => setDropdownOpen(false)}
                    className="profile-dropdown-item"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.75rem',
                      padding: '0.6rem 1rem',
                      fontSize: '0.88rem',
                      color: 'var(--text-primary)',
                      textDecoration: 'none',
                      transition: 'var(--transition)',
                    }}
                  >
                    <UserIcon size={16} style={{ color: 'var(--brand-highlight)' }} />
                    <span>My Profile</span>
                  </Link>
                </div>

                {/* 3. Divider */}
                <div style={{ borderTop: '1px solid var(--border-color)', margin: '0.25rem 0' }} />

                {/* 4. Logout Action */}
                <div style={{ padding: '0.25rem 0' }}>
                  <button
                    role="menuitem"
                    onClick={handleLogout}
                    className="profile-dropdown-item profile-dropdown-logout"
                    style={{
                      width: '100%',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.75rem',
                      padding: '0.6rem 1rem',
                      fontSize: '0.88rem',
                      color: 'var(--status-error)',
                      background: 'transparent',
                      border: 'none',
                      cursor: 'pointer',
                      textAlign: 'left',
                      transition: 'var(--transition)',
                    }}
                  >
                    <LogOut size={16} />
                    <span>Logout</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </header>
  );
};
