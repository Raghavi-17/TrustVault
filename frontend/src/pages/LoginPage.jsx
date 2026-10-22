import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Lock, User, AlertCircle, Sparkles } from 'lucide-react';
import { ThemeToggle } from '../components/ThemeToggle';

export const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username || !password) {
      setError('Please provide both username and password');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await login(username, password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.message || 'Invalid username or password');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickFill = (u, p) => {
    setUsername(u);
    setPassword(p);
    setError('');
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '2rem 1rem',
        backgroundColor: 'var(--bg-app)',
        position: 'relative',
      }}
    >
      <div style={{ position: 'absolute', top: '1.5rem', right: '1.5rem' }}>
        <ThemeToggle />
      </div>

      <div style={{ maxWidth: '440px', width: '100%' }}>
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div
            style={{
              width: '56px',
              height: '56px',
              backgroundColor: 'var(--brand-primary)',
              borderRadius: 'var(--radius-md)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#FFFFFF',
              boxShadow: 'var(--shadow-md)',
              marginBottom: '1rem',
            }}
          >
            <ShieldCheck size={32} />
          </div>
          <h1>TrustVault</h1>
          <p style={{ marginTop: '0.4rem' }}>
            Secure Document Sharing with a Tamper-Evident Audit Trail
          </p>
        </div>

        <div className="card" style={{ padding: '2rem' }}>
          {error && (
            <div className="alert alert-error">
              <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>{error}</div>
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label" htmlFor="username">
                Username
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="username"
                  type="text"
                  className="form-input"
                  style={{ paddingLeft: '2.5rem' }}
                  placeholder="Enter your username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={loading}
                  autoComplete="username"
                  required
                />
                <User
                  size={16}
                  style={{
                    position: 'absolute',
                    left: '0.85rem',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    color: 'var(--text-muted)',
                  }}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="password">
                Password
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="password"
                  type="password"
                  className="form-input"
                  style={{ paddingLeft: '2.5rem' }}
                  placeholder="••••••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={loading}
                  autoComplete="current-password"
                  required
                />
                <Lock
                  size={16}
                  style={{
                    position: 'absolute',
                    left: '0.85rem',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    color: 'var(--text-muted)',
                  }}
                />
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', padding: '0.75rem', marginTop: '0.5rem' }}
              disabled={loading}
            >
              {loading ? 'Authenticating...' : 'Sign In to Vault'}
            </button>
          </form>

          <div
            style={{
              marginTop: '1.75rem',
              paddingTop: '1.25rem',
              borderTop: '1px solid var(--border-color)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '0.75rem' }}>
              <Sparkles size={14} style={{ color: 'var(--brand-primary)' }} />
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
                Demo Accounts (Click to quick-fill)
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => handleQuickFill('admin', 'Admin@TrustVault2026!')}
              >
                Admin
              </button>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => handleQuickFill('owner_alice', 'Owner@TrustVault2026!')}
              >
                Owner (Alice)
              </button>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => handleQuickFill('owner_bob', 'OwnerBob@TrustVault2026!')}
              >
                Owner (Bob)
              </button>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => handleQuickFill('viewer_carol', 'Viewer@TrustVault2026!')}
              >
                Viewer (Carol)
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
