import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { 
  FileText, Search, Filter, Download, ExternalLink, Eye, Share2, 
  Trash2, Edit, RefreshCw, UploadCloud, ChevronLeft, ChevronRight,
  Shield, Check, AlertCircle
} from 'lucide-react';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { ConfirmDialog } from '../components/ConfirmDialog';

export const DocumentsPage = () => {
  const { user, isOwner, isAdmin } = useAuth();
  
  const [documents, setDocuments] = useState([]);
  const [categories, setCategories] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Download & action status
  const [actionError, setActionError] = useState('');
  const [downloadingId, setDownloadingId] = useState(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [accessFilter, setAccessFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState('createdAt');
  const [direction, setDirection] = useState('desc');

  // Modals state
  const [deleteModalDoc, setDeleteModalDoc] = useState(null);
  const [shareModalDoc, setShareModalDoc] = useState(null);
  const [activePermissions, setActivePermissions] = useState([]);
  const [selectedUserToShare, setSelectedUserToShare] = useState('');
  const [sharePermissionLevel, setSharePermissionLevel] = useState('READ');
  const [shareLoading, setShareLoading] = useState(false);
  const [shareFeedback, setShareFeedback] = useState({ type: '', message: '' });

  // Revoke permission confirmation state
  const [revokeTargetUser, setRevokeTargetUser] = useState(null);
  const [revokeLoading, setRevokeLoading] = useState(false);

  // Edit metadata modal
  const [editModalDoc, setEditModalDoc] = useState(null);
  const [editTitle, setEditTitle] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editCategory, setEditCategory] = useState('');
  const [editTags, setEditTags] = useState('');
  const [editLoading, setEditLoading] = useState(false);

  // Replace file modal
  const [replaceModalDoc, setReplaceModalDoc] = useState(null);
  const [replaceFile, setReplaceFile] = useState(null);
  const [replaceLoading, setReplaceLoading] = useState(false);
  const [replaceError, setReplaceError] = useState('');

  useEffect(() => {
    fetchCategories();
    fetchUsers();
  }, []);

  useEffect(() => {
    fetchDocuments();
  }, [currentPage, searchQuery, selectedCategory, sortBy, direction]);

  const fetchCategories = async () => {
    try {
      const res = await api.get('/categories');
      if (res?.data) setCategories(res.data);
    } catch (err) {
      console.error('Failed to load categories', err);
    }
  };

  const fetchUsers = async () => {
    try {
      const res = await api.get('/users');
      if (res?.data) setUsers(res.data);
    } catch (err) {
      console.error('Failed to load users', err);
    }
  };

  const fetchDocuments = async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({
        page: currentPage,
        size: 8,
        sortBy,
        direction,
      });
      if (searchQuery.trim()) params.append('query', searchQuery.trim());
      if (selectedCategory) params.append('categoryId', selectedCategory);

      const endpoint = searchQuery.trim() || selectedCategory ? `/documents/search?${params}` : `/documents?${params}`;
      const res = await api.get(endpoint);
      if (res?.data) {
        setDocuments(res.data.content || []);
        setTotalPages(res.data.totalPages || 0);
        setTotalElements(res.data.totalElements || 0);
      }
    } catch (err) {
      console.error('Failed to fetch documents', err);
    } finally {
      setLoading(false);
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  // Delete Document
  const handleDeleteConfirm = async () => {
    if (!deleteModalDoc) return;
    try {
      await api.delete(`/documents/${deleteModalDoc.id}`);
      setDeleteModalDoc(null);
      fetchDocuments();
    } catch (err) {
      alert(err.message || 'Failed to delete document');
    }
  };

  // Sharing Functions
  const openShareModal = async (doc) => {
    setShareModalDoc(doc);
    setShareFeedback({ type: '', message: '' });
    setSelectedUserToShare('');
    try {
      const res = await api.get(`/documents/${doc.id}/permissions`);
      if (res?.data) setActivePermissions(res.data);
    } catch (err) {
      console.error('Failed to fetch permissions', err);
    }
  };

  const handleGrantPermission = async (e) => {
    e.preventDefault();
    if (!selectedUserToShare) return;
    setShareLoading(true);
    setShareFeedback({ type: '', message: '' });
    try {
      await api.post(`/documents/${shareModalDoc.id}/permissions`, {
        userId: selectedUserToShare,
        permission: sharePermissionLevel,
      });
      setShareFeedback({ type: 'success', message: 'Permission granted successfully' });
      // Refresh permissions
      const res = await api.get(`/documents/${shareModalDoc.id}/permissions`);
      if (res?.data) setActivePermissions(res.data);
      setSelectedUserToShare('');
    } catch (err) {
      setShareFeedback({ type: 'error', message: err.message || 'Failed to grant permission' });
    } finally {
      setShareLoading(false);
    }
  };

  const handleConfirmRevoke = async () => {
    if (!revokeTargetUser) return;
    setRevokeLoading(true);
    try {
      await api.delete(`/documents/${shareModalDoc.id}/permissions/${revokeTargetUser.userId}`);
      setActivePermissions((prev) => prev.filter((p) => p.userId !== revokeTargetUser.userId));
      setShareFeedback({ type: 'success', message: `Access for ${revokeTargetUser.userFullName || revokeTargetUser.username} revoked immediately` });
      setRevokeTargetUser(null);
    } catch (err) {
      setShareFeedback({ type: 'error', message: err.message || 'Failed to revoke access' });
    } finally {
      setRevokeLoading(false);
    }
  };

  // Edit Metadata
  const openEditModal = (doc) => {
    setEditModalDoc(doc);
    setEditTitle(doc.title);
    setEditDesc(doc.description || '');
    setEditCategory(doc.category?.id || '');
    setEditTags(doc.tags || '');
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setEditLoading(true);
    try {
      await api.put(`/documents/${editModalDoc.id}`, {
        title: editTitle,
        description: editDesc,
        categoryId: editCategory || null,
        tags: editTags,
      });
      setEditModalDoc(null);
      fetchDocuments();
    } catch (err) {
      alert(err.message || 'Failed to update metadata');
    } finally {
      setEditLoading(false);
    }
  };

  // Replace Content
  const openReplaceModal = (doc) => {
    setReplaceModalDoc(doc);
    setReplaceFile(null);
    setReplaceError('');
  };

  const handleReplaceSubmit = async (e) => {
    e.preventDefault();
    if (!replaceFile) {
      setReplaceError('Please choose a replacement file');
      return;
    }
    setReplaceLoading(true);
    setReplaceError('');
    try {
      const formData = new FormData();
      formData.append('file', replaceFile);
      await api.upload(`/documents/${replaceModalDoc.id}/content`, formData);
      setReplaceModalDoc(null);
      fetchDocuments();
    } catch (err) {
      setReplaceError(err.message || 'Failed to replace document content');
    } finally {
      setReplaceLoading(false);
    }
  };

  const handleDownload = async (doc) => {
    try {
      setActionError('');
      setDownloadingId(doc.id);
      await api.download(doc.id, doc.originalFilename);
    } catch (err) {
      console.error('Download failed:', err);
      setActionError(err.message || 'Download failed: Access denied or document file missing');
    } finally {
      setDownloadingId(null);
    }
  };

  const getPermBadge = (perm) => {
    switch (perm) {
      case 'OWNER': return <Badge variant="success">OWNER</Badge>;
      case 'EDIT': return <Badge variant="warning">EDIT</Badge>;
      case 'DOWNLOAD': return <Badge variant="primary">DOWNLOAD</Badge>;
      case 'READ': return <Badge variant="info">READ</Badge>;
      case 'VIEW': return <Badge variant="neutral">VIEW</Badge>;
      default: return <Badge variant="neutral">NONE</Badge>;
    }
  };

  return (
    <div className="page-body">
      <div className="flex-between" style={{ marginBottom: '1.75rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1>Authorized Documents</h1>
          <p>Browse, inspect, download, and manage permissions on authorized secure documents</p>
        </div>
        {(isOwner || isAdmin) && (
          <Link to="/upload" className="btn btn-primary">
            <UploadCloud size={16} />
            <span>Upload New Document</span>
          </Link>
        )}
      </div>

      {/* Search & Filter Bar */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem', alignItems: 'center' }}>
          <div style={{ position: 'relative' }}>
            <input
              type="text"
              className="form-input"
              style={{ paddingLeft: '2.5rem' }}
              placeholder="Search by title or keyword..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setCurrentPage(0);
              }}
            />
            <Search
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

          <div>
            <select
              className="form-select"
              value={accessFilter}
              onChange={(e) => setAccessFilter(e.target.value)}
            >
              <option value="ALL">All Access Levels</option>
              <option value="OWNED">Owned by Me</option>
              <option value="SHARED">Shared with Me</option>
            </select>
          </div>

          <div>
            <select
              className="form-select"
              value={selectedCategory}
              onChange={(e) => {
                setSelectedCategory(e.target.value);
                setCurrentPage(0);
              }}
            >
              <option value="">All Categories</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <select
              className="form-select"
              value={`${sortBy}-${direction}`}
              onChange={(e) => {
                const [sb, dir] = e.target.value.split('-');
                setSortBy(sb);
                setDirection(dir);
              }}
            >
              <option value="createdAt-desc">Newest First</option>
              <option value="createdAt-asc">Oldest First</option>
              <option value="title-asc">Title (A - Z)</option>
              <option value="title-desc">Title (Z - A)</option>
              <option value="fileSizeBytes-desc">Size (Largest)</option>
              <option value="fileSizeBytes-asc">Size (Smallest)</option>
            </select>
          </div>
        </div>
      </div>

      {/* Action Feedback Banner */}
      {actionError && (
        <div className="alert alert-error" style={{ marginBottom: '1.25rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <AlertCircle size={16} />
            <span>{actionError}</span>
          </div>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={() => setActionError('')}
            style={{ padding: '0.15rem 0.5rem', fontSize: '0.75rem' }}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Documents Table */}
      <div className="card">
        {loading ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
            Retrieving documents from secure storage...
          </div>
        ) : documents.length === 0 ? (
          <div style={{ padding: '3.5rem 1rem', textAlign: 'center' }}>
            <FileText size={44} style={{ color: 'var(--text-muted)', marginBottom: '0.75rem' }} />
            <h3>No matching documents found</h3>
            <p style={{ marginTop: '0.25rem', color: 'var(--text-secondary)' }}>
              {searchQuery || selectedCategory || accessFilter !== 'ALL'
                ? 'Try adjusting your search criteria, category filter, or access view.'
                : 'Your secure vault is currently empty.'}
            </p>
            {(isOwner || isAdmin) && !searchQuery && !selectedCategory && accessFilter === 'ALL' && (
              <Link to="/upload" className="btn btn-primary btn-sm" style={{ marginTop: '1rem', display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
                <UploadCloud size={14} />
                <span>Upload Document</span>
              </Link>
            )}
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table">
              <thead>
                <tr>
                  <th>Title & Filename</th>
                  <th>Category</th>
                  <th>Owner</th>
                  <th>Size</th>
                  <th>Version</th>
                  <th>Permission</th>
                  <th>Uploaded & Updated</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents
                  .filter((doc) => {
                    const isDocOwner = doc.ownerId === user?.id || doc.effectivePermission === 'OWNER';
                    if (accessFilter === 'OWNED') return isDocOwner;
                    if (accessFilter === 'SHARED') return !isDocOwner;
                    return true;
                  })
                  .map((doc) => {
                    const canEdit = doc.effectivePermission === 'OWNER' || doc.effectivePermission === 'EDIT';
                    const isDocOwner = doc.ownerId === user?.id || doc.effectivePermission === 'OWNER';
                    const canDownload = doc.effectivePermission !== 'VIEW';

                    return (
                      <tr key={doc.id}>
                        <td>
                          <Link
                            to={`/documents/${doc.id}`}
                            style={{ fontWeight: 600, color: 'var(--text-primary)', textDecoration: 'none' }}
                          >
                            {doc.title}
                          </Link>
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.45rem', marginTop: '0.15rem' }}>
                            <span>{doc.originalFilename}</span>
                            <span
                              style={{
                                fontSize: '0.68rem',
                                padding: '0.1rem 0.35rem',
                                borderRadius: 'var(--radius-sm)',
                                backgroundColor: isDocOwner ? 'var(--status-success-bg)' : 'var(--status-info-bg)',
                                color: isDocOwner ? 'var(--status-success)' : 'var(--status-info)',
                                fontWeight: 600,
                              }}
                            >
                              {isDocOwner ? 'Owned' : 'Shared with you'}
                            </span>
                          </div>
                        </td>
                        <td>
                          <Badge variant="neutral">{doc.category?.name || 'General'}</Badge>
                        </td>
                        <td>{doc.ownerUsername}</td>
                        <td>{formatFileSize(doc.fileSizeBytes)}</td>
                        <td>
                          <span className="badge badge-neutral">v{doc.version}</span>
                        </td>
                        <td>{getPermBadge(doc.effectivePermission)}</td>
                        <td style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                          <div>{new Date(doc.createdAt).toLocaleDateString()}</div>
                          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Updated {new Date(doc.updatedAt).toLocaleDateString()}</div>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '0.4rem', alignItems: 'center' }}>
                          {/* Secure View Action: Opens document in existing secure viewing flow */}
                          <Link
                            to={`/documents/${doc.id}`}
                            className="btn btn-outline btn-sm"
                            title="View document details & cryptographic fingerprint"
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}
                          >
                            <Eye size={14} />
                            <span>View</span>
                          </Link>

                          {/* Secure Download Action: Permitted for OWNER, EDIT, DOWNLOAD, READ */}
                          {canDownload ? (
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => handleDownload(doc)}
                              disabled={downloadingId === doc.id}
                              title={`Download ${doc.originalFilename}`}
                              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}
                            >
                              <Download size={14} />
                              <span>{downloadingId === doc.id ? 'Downloading...' : 'Download'}</span>
                            </button>
                          ) : (
                            <button
                              className="btn btn-secondary btn-sm"
                              disabled
                              style={{ opacity: 0.45, cursor: 'not-allowed', display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}
                              title="Download restricted: VIEW permission only"
                            >
                              <Download size={14} />
                              <span>Download</span>
                            </button>
                          )}

                          {isDocOwner && (
                            <button
                              className="btn btn-outline btn-sm"
                              onClick={() => openShareModal(doc)}
                              title="Manage sharing & permissions"
                            >
                              <Share2 size={14} />
                            </button>
                          )}

                          {canEdit && (
                            <button
                              className="btn btn-outline btn-sm"
                              onClick={() => openEditModal(doc)}
                              title="Edit metadata"
                            >
                              <Edit size={14} />
                            </button>
                          )}

                          {canEdit && (
                            <button
                              className="btn btn-outline btn-sm"
                              onClick={() => openReplaceModal(doc)}
                              title="Replace file revision"
                            >
                              <RefreshCw size={14} />
                            </button>
                          )}

                          {isDocOwner && (
                            <button
                              className="btn btn-danger btn-sm"
                              onClick={() => setDeleteModalDoc(doc)}
                              title="Delete document"
                            >
                              <Trash2 size={14} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        {totalPages > 1 && (
          <div className="flex-between" style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid var(--border-color)' }}>
            <span style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
              Showing {documents.length} of {totalElements} documents (Page {currentPage + 1} of {totalPages})
            </span>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                disabled={currentPage === 0}
              >
                <ChevronLeft size={16} />
                <span>Prev</span>
              </button>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={currentPage >= totalPages - 1}
              >
                <span>Next</span>
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Share Document Modal */}
      <Modal
        isOpen={!!shareModalDoc}
        onClose={() => setShareModalDoc(null)}
        title={`Share "${shareModalDoc?.title}"`}
        maxWidth="580px"
      >
        <div>
          <p style={{ marginBottom: '1.25rem', fontSize: '0.88rem' }}>
            Grant VIEW, DOWNLOAD, READ, or EDIT access to registered users. Revocation takes effect immediately.
          </p>

          {shareFeedback.message && (
            <div className={`alert alert-${shareFeedback.type === 'success' ? 'success' : 'error'}`}>
              {shareFeedback.type === 'success' ? <Check size={18} /> : <AlertCircle size={18} />}
              <div>{shareFeedback.message}</div>
            </div>
          )}

          {/* Grant Form */}
          <form onSubmit={handleGrantPermission} style={{ marginBottom: '1.5rem' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr auto', gap: '0.75rem', alignItems: 'flex-end' }}>
              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Select User</label>
                <select
                  className="form-select"
                  value={selectedUserToShare}
                  onChange={(e) => setSelectedUserToShare(e.target.value)}
                  required
                >
                  <option value="">Choose user...</option>
                  {users
                    .filter((u) => u.id !== user?.id && !activePermissions.some((p) => p.userId === u.id))
                    .map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.fullName} ({u.username})
                      </option>
                    ))}
                </select>
              </div>

              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Permission</label>
                <select
                  className="form-select"
                  value={sharePermissionLevel}
                  onChange={(e) => setSharePermissionLevel(e.target.value)}
                >
                  <option value="VIEW">VIEW (Details only, no download)</option>
                  <option value="DOWNLOAD">DOWNLOAD (View & download)</option>
                  <option value="EDIT">EDIT (View, download & edit)</option>
                  <option value="READ">READ (Legacy view & download)</option>
                </select>
              </div>

              <button type="submit" className="btn btn-primary" disabled={shareLoading || !selectedUserToShare}>
                {shareLoading ? 'Granting...' : 'Grant'}
              </button>
            </div>
          </form>

          {/* Current Grants Table */}
          <h4 style={{ fontSize: '0.95rem', marginBottom: '0.75rem' }}>Active Grants</h4>
          {activePermissions.length === 0 ? (
            <div style={{ padding: '1.5rem', textAlign: 'center', background: 'var(--bg-surface)', borderRadius: 'var(--radius-sm)', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
              No other users currently have access to this document.
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table" style={{ fontSize: '0.82rem' }}>
                <thead>
                  <tr>
                    <th>User</th>
                    <th>Permission</th>
                    <th style={{ textAlign: 'right' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {activePermissions.map((perm) => (
                    <tr key={perm.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{perm.userFullName}</div>
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>@{perm.username}</div>
                      </td>
                      <td>
                        <Badge variant={perm.permission === 'EDIT' ? 'warning' : perm.permission === 'DOWNLOAD' ? 'primary' : perm.permission === 'VIEW' ? 'neutral' : 'info'}>
                          {perm.permission}
                        </Badge>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button
                          className="btn btn-danger btn-sm"
                          style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }}
                          onClick={() => setRevokeTargetUser(perm)}
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
      </Modal>

      {/* Edit Metadata Modal */}
      <Modal
        isOpen={!!editModalDoc}
        onClose={() => setEditModalDoc(null)}
        title="Edit Document Metadata"
      >
        <form onSubmit={handleEditSubmit}>
          <div className="form-group">
            <label className="form-label">Document Title</label>
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
            <label className="form-label">Tags (comma separated)</label>
            <input
              type="text"
              className="form-input"
              value={editTags}
              onChange={(e) => setEditTags(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setEditModalDoc(null)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={editLoading}>
              {editLoading ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Replace Content Modal */}
      <Modal
        isOpen={!!replaceModalDoc}
        onClose={() => setReplaceModalDoc(null)}
        title={`Replace File Content — ${replaceModalDoc?.title}`}
      >
        <form onSubmit={handleReplaceSubmit}>
          <p style={{ marginBottom: '1rem', fontSize: '0.88rem' }}>
            Upload a revised file. The version will automatically increment from <strong>v{replaceModalDoc?.version}</strong> to <strong>v{(replaceModalDoc?.version || 1) + 1}</strong>.
          </p>

          {replaceError && (
            <div className="alert alert-error">
              <AlertCircle size={18} />
              <div>{replaceError}</div>
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Select Replacement File (Max 15MB)</label>
            <input
              type="file"
              className="form-input"
              onChange={(e) => setReplaceFile(e.target.files[0])}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setReplaceModalDoc(null)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={replaceLoading || !replaceFile}>
              {replaceLoading ? 'Replacing...' : 'Upload Revision'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!deleteModalDoc}
        onClose={() => setDeleteModalDoc(null)}
        onConfirm={handleDeleteConfirm}
        title="Delete Document"
        message={`Are you sure you want to permanently delete "${deleteModalDoc?.title}"? This will remove the file from secure storage and revoke all permissions. This action cannot be undone.`}
        confirmText="Permanently Delete"
        danger
      />

      {/* Revoke Access Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!revokeTargetUser}
        onClose={() => setRevokeTargetUser(null)}
        onConfirm={handleConfirmRevoke}
        title="Revoke Document Access"
        message={`Are you sure you want to revoke access for ${revokeTargetUser?.userFullName || revokeTargetUser?.username}? They will immediately lose access to "${shareModalDoc?.title}".`}
        confirmText="Revoke Access"
        danger
        loading={revokeLoading}
      />
    </div>
  );
};
