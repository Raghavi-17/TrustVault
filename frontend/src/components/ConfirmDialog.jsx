import React from 'react';
import { Modal } from './Modal';
import { AlertTriangle } from 'lucide-react';

export const ConfirmDialog = ({
  isOpen,
  onClose,
  onConfirm,
  title = 'Confirm Action',
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  danger = false,
  loading = false,
}) => {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title} maxWidth="450px">
      <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-start', margin: '0.5rem 0 1.5rem 0' }}>
        {danger && (
          <div style={{ background: 'var(--status-error-bg)', padding: '0.6rem', borderRadius: '50%', color: 'var(--status-error)' }}>
            <AlertTriangle size={24} />
          </div>
        )}
        <p style={{ margin: 0, fontSize: '0.95rem', lineHeight: '1.5' }}>{message}</p>
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
        <button className="btn btn-secondary" onClick={onClose} disabled={loading}>
          {cancelText}
        </button>
        <button
          className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`}
          onClick={onConfirm}
          disabled={loading}
        >
          {loading ? 'Processing...' : confirmText}
        </button>
      </div>
    </Modal>
  );
};
