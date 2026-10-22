import React from 'react';
import { Link } from 'react-router-dom';
import { ShieldAlert, ArrowLeft } from 'lucide-react';

export const AccessDeniedPage = () => {
  return (
    <div
      style={{
        minHeight: '80vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '2rem',
        textAlign: 'center',
      }}
    >
      <div className="card" style={{ maxWidth: '480px', padding: '3rem 2rem' }}>
        <div
          style={{
            width: '64px',
            height: '64px',
            backgroundColor: 'var(--status-error-bg)',
            borderRadius: '50%',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--status-error)',
            marginBottom: '1.25rem',
          }}
        >
          <ShieldAlert size={36} />
        </div>
        <h2 style={{ marginBottom: '0.5rem' }}>403 — Access Forbidden</h2>
        <p style={{ marginBottom: '1.75rem', lineHeight: '1.6' }}>
          You do not possess the required role or per-document authorization to access this resource. All unauthorized access attempts are logged in the cryptographic audit trail.
        </p>
        <Link to="/dashboard" className="btn btn-primary" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
          <ArrowLeft size={16} />
          <span>Return to Dashboard</span>
        </Link>
      </div>
    </div>
  );
};
