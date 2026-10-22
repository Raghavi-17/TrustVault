import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { 
  ArrowLeft, Download, Share2, Edit, RefreshCw, Trash2, 
  Copy, Check, Shield, FileText, Clock, User, HardDrive, Tag
} from 'lucide-react';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { ConfirmDialog } from '../components/ConfirmDialog';

export const DocumentDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [document, setDocument] = useState(null);
  const [permissions, setPermissions] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [copiedHash, setCopiedHash] = useState(false);

  // Sharing state
  const [isShareOpen, setIsShareOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState('');
  const [permissionLevel, setPermissionLevel] = useState('READ');
  const [shareLoading, setShareLoading] = useState(false);
  const [shareFeedback, setShareFeedback] = useState({ type: '', message: '' });

  // Delete state
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);

  // Edit metadata state
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editTags, setEditTags] = useState('');
  const [categories, setCategories] = useState([]);
  const [editCategory, setEditCategory] = useState('');
  const [editLoading, setEditLoading] = useState(false);

  // Replace file state
  const [isReplaceOpen, setIsReplaceOpen] = useState(false);
  const [replaceFile, setReplaceFile] = useState(null);
  const [replaceLoading, setReplaceLoading] = useState(false);
  const [replaceError, setReplaceError] = useState('');

  useEffect(() => {
    fetchDoc();
    fetchCategories();
    fetchUsers();
  }, [id]);

  const fetchDoc = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await api.get(`/documents/${id}`);
      if (res?.data) {
        setDocument(res.data);
        if (res.data.effectivePermission === 'OWNER') {
          fetchPermissions();
        }
      }
    } catch (err) {
      setError(err.message || 'Failed to load document details or access denied');
    } finally {
      setLoading(false);
    }
  };

  const fetchPermissions = async () => {
    try {
      const res = await api.get(`/documents/${id}/permissions`);
      if (res?.data) setPermissions(res.data);
    } catch (err) {
      console.error('Failed to load permissions', err);
    }
  };

  const fetchCategories = async () => {
    try {
      const res = await api.get('/categories');
      if (res?.data) setCategories(res.data);
    } catch (err) {}
  };

  const fetchUsers = async () => {
    try {
      const res = await api.get('/users');
      if (res?.data) setUsers(res.data);
    } catch (err) {}
  };

  const handleCopyHash = () => {
    if (!document?.sha256Hash) return;
    navigator.clipboard.writeText(document.sha256Hash);
    setCopiedHash(true);
    setTimeout(() => setCopiedHash(false), 2000);
  };

  const handleGrant = async (e) => {
    e.preventDefault();
    if (!selectedUser) return;
    setShareLoading(true);
    setShareFeedback({ type: '', message: '' });
    try {
      await api.post(`/documents/${id}/permissions`, {
        userId: selectedUser,
        permission: permissionLevel,
      });
      setShareFeedback({ type: 'success', message: 'Permission granted' });
      fetchPermissions();
      setSelectedUser('');
    } catch (err) {
      setShareFeedback({ type: 'error', message: err.message || 'Failed to grant permission' });
    } finally {
      setShareLoading(false);
    }
  };

  const handleRevoke = async (targetUserId) => {
    try {
      await api.delete(`/documents/${id}/permissions/${targetUserId}`);
      setPermissions((prev) => prev.filter((p) => p.userId !== targetUserId));
    } catch (err) {
      alert(err.message || 'Failed to revoke permission');
    }
  };

  const handleDelete = async () => {
    try {
      await api.delete(`/documents/${id}`);
      navigate('/documents');
    } catch (err) {
      alert(err.message || 'Failed to delete document');
    }
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setEditLoading(true);
    try {
      await api.put(`/documents/${id}`, {
        title: editTitle,
        description: editDesc,
        categoryId: editCategory || null,
        tags: editTags,
      });
      setIsEditOpen(false);
      fetchDoc();
    } catch (err) {
      alert(err.message || 'Failed to update metadata');
    } finally {
      setEditLoading(false);
    }
  };

  const handleReplaceSubmit = async (e) => {
    e.preventDefault();
    if (!replaceFile) return;
    setReplaceLoading(true);
    setReplaceError('');
    try {
      const formData = new FormData();
      formData.append('file', replaceFile);
      await api.upload(`/documents/${id}/content`, formData);
      setIsReplaceOpen(false);
      fetchDoc();
    } catch (err) {
      setReplaceError(err.message || 'Failed to replace file content');
    } finally {
      setReplaceLoading(false);
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  if (loading) {
    return (
      <div className="page-body" style={{ textAlign: 'center', padding: '5rem 0' }}>
        <p>Loading document details from vault...</p>
      </div>
    );
  }

  if (error || !document) {
    return (
      <div className="page-body" style={{ maxWidth: '600px', textAlign: 'center', padding: '4rem 1rem' }}>
        <div className="card" style={{ padding: '2.5rem' }}>
          <Shield size={44} style={{ color: 'var(--status-error)', marginBottom: '1rem' }} />
          <h3>Access Restricted or Document Missing</h3>
          <p style={{ marginTop: '0.5rem', marginBottom: '1.5rem' }}>
            {error || 'You do not have permission to view or download this document.'}
          </p>
          <Link to="/documents" className="btn btn-primary">
            Back to Documents
          </Link>
        </div>
      </div>
    );
  }

  const isDocOwner = document.effectivePermission === 'OWNER';
  const canEdit = isDocOwner || document.effectivePermission === 'EDIT';

  return (
    <div className="page-body">
      <div style={{ marginBottom: '1.5rem' }}>
        <Link to="/documents" className="btn btn-outline btn-sm" style={{ marginBottom: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}>
          <ArrowLeft size={14} />
          <span>Back to Documents</span>
        </Link>
        <div className="flex-between" style={{ flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.3rem' }}>
              <h1>{document.title}</h1>
              <span className="badge badge-neutral">v{document.version}</span>
              <Badge variant={isDocOwner ? 'success' : canEdit ? 'warning' : 'info'}>
                {document.effectivePermission}
              </Badge>
            </div>
            <p>{document.originalFilename}</p>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button
              className="btn btn-primary"
              onClick={() => api.download(document.id, document.originalFilename)}
            >
              <Download size={16} />
              <span>Download File</span>
            </button>

            {isDocOwner && (
              <button className="btn btn-secondary" onClick={() => setIsShareOpen(true)}>
                <Share2 size={16} />
                <span>Share</span>
              </button>
            )}

            {canEdit && (
              <button
                className="btn btn-outline"
                onClick={() => {
                  setEditTitle(document.title);
                  setEditDesc(document.description || '');
                  setEditCategory(document.category?.id || '');
                  setEditTags(document.tags || '');
                  setIsEditOpen(true);
                }}
              >
                <Edit size={16} />
                <span>Edit</span>
              </button>
            )}

            {canEdit && (
              <button className="btn btn-outline" onClick={() => setIsReplaceOpen(true)}>
                <RefreshCw size={16} />
                <span>Replace File</span>
              </button>
            )}

            {isDocOwner && (
              <button className="btn btn-danger" onClick={() => setIsDeleteOpen(true)}>
                <Trash2 size={16} />
                <span>Delete</span>
              </button>
            )}
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.5rem' }}>
        {/* Left Column: Metadata & Security Fingerprint */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div className="card">
            <h3 style={{ marginBottom: '1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.5rem' }}>
              Document Information
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', fontSize: '0.9rem' }}>
              <div>
                <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>Description</span>
                <span style={{ fontWeight: 500 }}>{document.description || 'No description provided.'}</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                <div>
                  <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>Category</span>
                  <Badge variant="neutral">{document.category?.name || 'General'}</Badge>
                </div>
                <div>
                  <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>Owner</span>
                  <span style={{ fontWeight: 600 }}>{document.ownerUsername}</span>
                </div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                <div>
                  <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>File Size</span>
                  <span>{formatFileSize(document.fileSizeBytes)}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>MIME Content Type</span>
                  <span className="font-mono" style={{ fontSize: '0.8rem' }}>{document.mimeType}</span>
                </div>
              </div>
              <div>
                <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>Tags</span>
                <span>{document.tags || 'None'}</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', borderTop: '1px solid var(--border-subtle)', paddingTop: '0.75rem' }}>
                <div>
                  <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>Created</span>
                  <span style={{ fontSize: '0.82rem' }}>{new Date(document.createdAt).toLocaleString()}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-secondary)', display: 'block', fontSize: '0.78rem' }}>Last Modified</span>
                  <span style={{ fontSize: '0.82rem' }}>{new Date(document.updatedAt).toLocaleString()}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Cryptographic Hash Fingerprint */}
          <div className="card">
            <h3 style={{ marginBottom: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Shield size={18} style={{ color: 'var(--brand-primary)' }} />
              <span>SHA-256 Checksum Fingerprint</span>
            </h3>
            <p style={{ fontSize: '0.82rem', marginBottom: '0.75rem' }}>
              Cryptographic hash calculated during upload for end-to-end data integrity.
            </p>
            <div
              style={{
                background: 'var(--bg-surface)',
                padding: '0.75rem',
                borderRadius: 'var(--radius-sm)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '0.5rem',
                border: '1px solid var(--border-color)',
              }}
            >
              <span className="font-mono" style={{ fontSize: '0.75rem', wordBreak: 'break-all' }}>
                {document.sha256Hash}
              </span>
              <button
                className="btn btn-outline btn-sm"
                onClick={handleCopyHash}
                title="Copy SHA-256 Hash"
                style={{ flexShrink: 0 }}
              >
                {copiedHash ? <Check size={14} style={{ color: 'var(--status-success)' }} /> : <Copy size={14} />}
              </button>
            </div>
          </div>
        </div>

        {/* Right Column: Permissions & Access Grants */}
        <div>
          <div className="card">
            <div className="flex-between" style={{ marginBottom: '1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.5rem' }}>
              <h3>Access Control List</h3>
              {isDocOwner && (
                <button className="btn btn-secondary btn-sm" onClick={() => setIsShareOpen(true)}>
                  <Share2 size={14} />
                  <span>Grant Access</span>
                </button>
              )}
            </div>

            {!isDocOwner ? (
              <p style={{ fontSize: '0.88rem' }}>
                You have been granted <strong>{document.effectivePermission}</strong> access by the document owner. Only the owner can manage permissions.
              </p>
            ) : permissions.length === 0 ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                <p>This document is private to you. No other users have been granted access.</p>
              </div>
            ) : (
              <div className="table-responsive">
                <table className="table">
                  <thead>
                    <tr>
                      <th>User</th>
                      <th>Level</th>
                      <th style={{ textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {permissions.map((p) => (
                      <tr key={p.id}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{p.userFullName}</div>
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>@{p.username}</div>
                        </td>
                        <td>
                          <Badge variant={p.permission === 'EDIT' ? 'warning' : 'info'}>
                            {p.permission}
                          </Badge>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <button
                            className="btn btn-danger btn-sm"
                            style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }}
                            onClick={() => handleRevoke(p.userId)}
                          >
                            Revoke
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Share Modal */}
      <Modal isOpen={isShareOpen} onClose={() => setIsShareOpen(false)} title={`Share "${document.title}"`}>
        <form onSubmit={handleGrant}>
          {shareFeedback.message && (
            <div className={`alert alert-${shareFeedback.type === 'success' ? 'success' : 'error'}`}>
              <div>{shareFeedback.message}</div>
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Select Registered User</label>
            <select
              className="form-select"
              value={selectedUser}
              onChange={(e) => setSelectedUser(e.target.value)}
              required
            >
              <option value="">Select a user...</option>
              {users
                .filter((u) => u.id !== user?.id && !permissions.some((p) => p.userId === u.id))
                .map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName} (@{u.username})
                  </option>
                ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Permission Level</label>
            <select
              className="form-select"
              value={permissionLevel}
              onChange={(e) => setPermissionLevel(e.target.value)}
            >
              <option value="READ">READ (View and download only)</option>
              <option value="EDIT">EDIT (View, download, edit metadata, and replace content)</option>
            </select>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsShareOpen(false)}>
              Done
            </button>
            <button type="submit" className="btn btn-primary" disabled={shareLoading || !selectedUser}>
              {shareLoading ? 'Granting...' : 'Grant Access'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Edit Metadata Modal */}
      <Modal isOpen={isEditOpen} onClose={() => setIsEditOpen(false)} title="Edit Document Metadata">
        <form onSubmit={handleEditSubmit}>
          <div className="form-group">
            <label className="form-label">Title</label>
            <input
              type="text"
              className="form-input"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Description</label>
            <textarea
              className="form-textarea"
              rows="3"
              value={editDesc}
              onChange={(e) => setEditDesc(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Category</label>
            <select
              className="form-select"
              value={editCategory}
              onChange={(e) => setEditCategory(e.target.value)}
            >
              <option value="">None (Uncategorized)</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Tags</label>
            <input
              type="text"
              className="form-input"
              value={editTags}
              onChange={(e) => setEditTags(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsEditOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={editLoading}>
              {editLoading ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Replace File Modal */}
      <Modal isOpen={isReplaceOpen} onClose={() => setIsReplaceOpen(false)} title="Replace File Content">
        <form onSubmit={handleReplaceSubmit}>
          <p style={{ marginBottom: '1rem', fontSize: '0.88rem' }}>
            Uploading a new file will increment the document version to <strong>v{document.version + 1}</strong>.
          </p>

          {replaceError && (
            <div className="alert alert-error">
              <div>{replaceError}</div>
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Select Replacement File</label>
            <input
              type="file"
              className="form-input"
              onChange={(e) => setReplaceFile(e.target.files[0])}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsReplaceOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={replaceLoading || !replaceFile}>
              {replaceLoading ? 'Uploading...' : 'Replace'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={isDeleteOpen}
        onClose={() => setIsDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Delete Document"
        message={`Are you sure you want to permanently delete "${document.title}"? This action cannot be undone.`}
        confirmText="Delete Permanently"
        danger
      />
    </div>
  );
};
