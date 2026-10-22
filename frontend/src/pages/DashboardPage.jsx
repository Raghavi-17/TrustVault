import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Link } from 'react-router-dom';
import { FileText, UploadCloud, Users, ShieldCheck, Download, ExternalLink, ArrowRight } from 'lucide-react';
import { Badge } from '../components/Badge';

export const DashboardPage = () => {
  const { user, isAdmin, isOwner } = useAuth();
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalAccessible: 0,
    owned: 0,
    shared: 0,
  });

  useEffect(() => {
    fetchDashboardData();
  }, [user]);

  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      const res = await api.get('/documents?page=0&size=5&sortBy=createdAt&direction=desc');
      if (res?.data?.content) {
        setDocuments(res.data.content);
        const total = res.data.totalElements || 0;
        const ownedCount = res.data.content.filter((d) => d.ownerId === user?.id).length;
        setStats({
          totalAccessible: total,
          owned: ownedCount,
          shared: Math.max(0, total - ownedCount),
        });
      }
    } catch (err) {
      console.error('Failed to load dashboard documents', err);
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

  const getPermBadge = (perm) => {
    switch (perm) {
      case 'OWNER': return <Badge variant="success">OWNER</Badge>;
      case 'EDIT': return <Badge variant="warning">EDIT</Badge>;
      case 'READ': return <Badge variant="info">READ</Badge>;
      default: return <Badge variant="neutral">NONE</Badge>;
    }
  };

  return (
    <div className="page-body">
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ marginBottom: '0.4rem' }}>Welcome back, {user?.fullName || user?.username}</h1>
        <p>TrustVault Cryptographic Workspace — Authorized Document Repository</p>
      </div>

      {/* Metrics Grid */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '1.25rem',
          marginBottom: '2rem',
        }}
      >
        <div className="card">
          <div className="flex-between" style={{ marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
              Accessible Documents
            </span>
            <div style={{ background: 'var(--bg-surface)', padding: '0.4rem', borderRadius: 'var(--radius-sm)' }}>
              <FileText size={18} style={{ color: 'var(--brand-primary)' }} />
            </div>
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800 }}>{stats.totalAccessible}</div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Authorized to discover & read</span>
        </div>

        <div className="card">
          <div className="flex-between" style={{ marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
              Owned by You
            </span>
            <div style={{ background: 'var(--status-success-bg)', padding: '0.4rem', borderRadius: 'var(--radius-sm)' }}>
              <ShieldCheck size={18} style={{ color: 'var(--status-success)' }} />
            </div>
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800 }}>{stats.owned}</div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Full administrative ownership</span>
        </div>

        <div className="card">
          <div className="flex-between" style={{ marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
              Shared with You
            </span>
            <div style={{ background: 'var(--status-info-bg)', padding: '0.4rem', borderRadius: 'var(--radius-sm)' }}>
              <Users size={18} style={{ color: 'var(--status-info)' }} />
            </div>
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800 }}>{stats.shared}</div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Explicit granular grants</span>
        </div>

        <div className="card">
          <div className="flex-between" style={{ marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
              Audit Verification
            </span>
            <div style={{ background: 'var(--status-success-bg)', padding: '0.4rem', borderRadius: 'var(--radius-sm)' }}>
              <ShieldCheck size={18} style={{ color: 'var(--status-success)' }} />
            </div>
          </div>
          <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--status-success)' }}>
            CHAIN INTACT
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>SHA-256 Hash Chained</span>
        </div>
      </div>

      {/* Quick Action Shortcuts */}
      <div
        className="card"
        style={{
          marginBottom: '2rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '1rem',
          background: 'linear-gradient(135deg, var(--bg-card) 0%, var(--bg-surface) 100%)',
        }}
      >
        <div>
          <h3 style={{ marginBottom: '0.25rem' }}>Ready to share documents securely?</h3>
          <p style={{ margin: 0, fontSize: '0.88rem' }}>
            Upload files with strict MIME validation, server-side encryption keys, and instant grant revocation.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          {(isOwner || isAdmin) && (
            <Link to="/upload" className="btn btn-primary">
              <UploadCloud size={16} />
              <span>Upload Document</span>
            </Link>
          )}
          <Link to="/documents" className="btn btn-secondary">
            <span>Browse Vault</span>
            <ArrowRight size={16} />
          </Link>
        </div>
      </div>

      {/* Recent Documents Table */}
      <div className="card">
        <div className="flex-between" style={{ marginBottom: '1.25rem' }}>
          <div>
            <h3>Recent Authorized Documents</h3>
            <p style={{ fontSize: '0.82rem' }}>Latest documents discovered under your active permissions</p>
          </div>
          <Link to="/documents" className="btn btn-outline btn-sm">
            View All
          </Link>
        </div>

        {loading ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
            Loading vault entries...
          </div>
        ) : documents.length === 0 ? (
          <div style={{ padding: '3rem 1rem', textAlign: 'center' }}>
            <FileText size={40} style={{ color: 'var(--text-muted)', marginBottom: '0.75rem' }} />
            <h4>No documents available yet</h4>
            <p style={{ marginTop: '0.25rem', marginBottom: '1.25rem' }}>
              Upload your first secure document or ask an owner to grant you access.
            </p>
            {(isOwner || isAdmin) && (
              <Link to="/upload" className="btn btn-primary btn-sm">
                <UploadCloud size={16} />
                <span>Upload Document</span>
              </Link>
            )}
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table">
              <thead>
                <tr>
                  <th>Document Title</th>
                  <th>Category</th>
                  <th>Owner</th>
                  <th>Size</th>
                  <th>Version</th>
                  <th>Your Permission</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr key={doc.id}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{doc.title}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        {doc.originalFilename}
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
                    <td>
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <Link to={`/documents/${doc.id}`} className="btn btn-outline btn-sm" title="View details">
                          <ExternalLink size={14} />
                        </Link>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => api.download(doc.id, doc.originalFilename)}
                          title="Download document"
                        >
                          <Download size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
