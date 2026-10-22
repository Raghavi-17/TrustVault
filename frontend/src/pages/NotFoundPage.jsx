import React from 'react';
import { Link } from 'react-router-dom';
import { HelpCircle, ArrowLeft } from 'lucide-react';

export const NotFoundPage = () => {
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
      <div className="card" style={{ maxWidth: '440px', padding: '3rem 2rem' }}>
        <div
          style={{
            width: '64px',
            height: '64px',
            backgroundColor: 'var(--bg-surface)',
            borderRadius: '50%',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--text-secondary)',
            marginBottom: '1.25rem',
          }}
        >
          <HelpCircle size={36} />
        </div>
        <h2 style={{ marginBottom: '0.5rem' }}>404 — Page Not Found</h2>
        <p style={{ marginBottom: '1.75rem', lineHeight: '1.6' }}>
          The requested path does not exist in TrustVault or may have been restricted.
        </p>
        <Link to="/dashboard" className="btn btn-primary" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
          <ArrowLeft size={16} />
          <span>Back to Safety</span>
        </Link>
      </div>
    </div>
  );
};
