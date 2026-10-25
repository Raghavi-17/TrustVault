import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { 
  Users, UserPlus, ShieldAlert, ShieldCheck, CheckCircle2, 
  XCircle, AlertTriangle, Activity, Lock, RefreshCw, Clock, Check, X, Search
} from 'lucide-react';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { ConfirmDialog } from '../components/ConfirmDialog';

export const AdminPage = () => {
  const { user: currentUser } = useAuth();
  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState([]);
  const [pendingUsers, setPendingUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');

  // Status toggle state
  const [statusUser, setStatusUser] = useState(null);
  const [statusActionLoading, setStatusActionLoading] = useState(false);

  // Reject confirmation state
  const [rejectUser, setRejectUser] = useState(null);

  // Approval action states
  const [actionInProgressId, setActionInProgressId] = useState(null);
  const [roleSelectionMap, setRoleSelectionMap] = useState({});

  // User directory filters
  const [userSearch, setUserSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  // Create user modal
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newFullName, setNewFullName] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [newRole, setNewRole] = useState('VIEWER');
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState('');

  useEffect(() => {
    fetchAdminData();
  }, []);

  const fetchAdminData = async () => {
    try {
      setLoading(true);
      setError('');
      const [statsRes, usersRes, pendingRes] = await Promise.all([
        api.get('/admin/dashboard'),
        api.get('/users'),
        api.get('/admin/registrations/pending'),
      ]);
      if (statsRes?.data) setStats(statsRes.data);
      if (usersRes?.data) setUsers(usersRes.data);
      if (pendingRes?.data) setPendingUsers(pendingRes.data);
    } catch (err) {
      setError(err.message || 'Failed to load administrative oversight data');
    } finally {
      setLoading(false);
    }
  };

  const handleApproveRegistration = async (user) => {
    setActionInProgressId(user.id);
    setError('');
    setActionSuccess('');
    try {
      const assignedRole = roleSelectionMap[user.id] || 'VIEWER';
      await api.post(`/admin/registrations/${user.id}/approve`, { role: assignedRole });
      setActionSuccess(`Approved registration for ${user.fullName} (${user.email}) as ${assignedRole}.`);
      await fetchAdminData();
    } catch (err) {
      setError(err.message || 'Failed to approve registration');
    } finally {
      setActionInProgressId(null);
    }
  };

  const handleConfirmReject = async () => {
    if (!rejectUser) return;
    const userToReject = rejectUser;
    setActionInProgressId(userToReject.id);
    setError('');
    setActionSuccess('');
    try {
      await api.post(`/admin/registrations/${userToReject.id}/reject`);
      setActionSuccess(`Rejected registration request for ${userToReject.fullName}.`);
      setRejectUser(null);
      await fetchAdminData();
    } catch (err) {
      setError(err.message || 'Failed to reject registration');
    } finally {
      setActionInProgressId(null);
    }
  };

  const handleToggleUserStatus = async () => {
    if (!statusUser) return;
    if (statusUser.id === currentUser?.id || statusUser.username === currentUser?.username) {
      setError('Safety constraint: You cannot deactivate your own logged-in administrator account.');
      setStatusUser(null);
      return;
    }
    setStatusActionLoading(true);
    try {
      const newStatus = !statusUser.isActive;
      await api.put(`/users/${statusUser.id}/status`, { isActive: newStatus });
      setStatusUser(null);
      fetchAdminData();
    } catch (err) {
      alert(err.message || 'Failed to update user status');
    } finally {
      setStatusActionLoading(false);
    }
  };

  const handleCreateUser = async (e) => {
    e.preventDefault();
    setCreateLoading(true);
    setCreateError('');
    try {
      await api.post('/users', {
        username: newUsername.trim(),
        password: newPassword,
        fullName: newFullName.trim(),
        email: newEmail.trim(),
        role: newRole,
      });
      setIsCreateOpen(false);
      setNewUsername('');
      setNewPassword('');
      setNewFullName('');
      setNewEmail('');
      setNewRole('VIEWER');
      fetchAdminData();
    } catch (err) {
      setCreateError(err.message || 'Failed to create user');
    } finally {
      setCreateLoading(false);
    }
  };

  const getRoleBadgeVariant = (role) => {
    switch (role) {
      case 'ADMIN': return 'copper';
      case 'OWNER': return 'success';
      case 'VIEWER': return 'info';
      default: return 'neutral';
    }
  };

  const getStatusBadge = (user) => {
    const status = user.status || (user.isActive ? 'ACTIVE' : 'DEACTIVATED');
    switch (status) {
      case 'ACTIVE':
        return (
          <Badge variant="success">
            <CheckCircle2 size={12} />
            <span>Active</span>
          </Badge>
        );
      case 'PENDING':
        return (
          <Badge variant="warning">
            <Clock size={12} />
            <span>Pending Approval</span>
          </Badge>
        );
      case 'REJECTED':
        return (
          <Badge variant="error">
            <XCircle size={12} />
            <span>Rejected</span>
          </Badge>
        );
      case 'DEACTIVATED':
      default:
        return (
          <Badge variant="error">
            <XCircle size={12} />
            <span>Deactivated</span>
          </Badge>
        );
    }
  };

  return (
    <div className="page-body">
      <div className="flex-between" style={{ marginBottom: '1.75rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1>Administrative Governance</h1>
          <p>Account lifecycle management, user directory, and security oversight</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button className="btn btn-secondary" onClick={fetchAdminData}>
            <RefreshCw size={15} />
            <span>Refresh</span>
          </button>
          <button className="btn btn-primary" onClick={() => setIsCreateOpen(true)}>
            <UserPlus size={16} />
            <span>Provision User</span>
          </button>
        </div>
      </div>

      {actionSuccess && (
        <div className="alert alert-success" style={{ marginBottom: '1.5rem' }}>
          <CheckCircle2 size={18} />
          <div>{actionSuccess}</div>
        </div>
      )}

      {error && (
        <div className="alert alert-error" style={{ marginBottom: '1.5rem' }}>
          <AlertTriangle size={18} />
          <div>{error}</div>
        </div>
      )}

      {/* Security Metrics Cards */}
      {stats && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: '1rem',
            marginBottom: '2rem',
          }}
        >
          <div className="card">
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Total Users</span>
            <div style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0.25rem 0' }}>{stats.totalUsers}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--status-success)' }}>
              {stats.activeUsers} Active / {stats.deactivatedUsers} Inactive
            </div>
          </div>

          <div
            className="card"
            style={stats.pendingRegistrations > 0 ? { border: '2px solid var(--status-warning)' } : {}}
          >
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Pending Approvals</span>
            <div
              style={{
                fontSize: '1.75rem',
                fontWeight: 800,
                margin: '0.25rem 0',
                color: stats.pendingRegistrations > 0 ? 'var(--status-warning)' : 'inherit',
              }}
            >
              {stats.pendingRegistrations}
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              {stats.pendingRegistrations > 0 ? 'Requires administrator action' : 'No requests pending'}
            </div>
          </div>

          <div className="card">
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Total Uploads</span>
            <div style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0.25rem 0' }}>{stats.totalUploads}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Private files encrypted</div>
          </div>

          <div className="card">
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Total Downloads</span>
            <div style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0.25rem 0' }}>{stats.totalDownloads}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Authorized retrievals</div>
          </div>

          <div className="card">
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Access Denied Events</span>
            <div style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0.25rem 0', color: stats.totalAccessDenied > 0 ? 'var(--status-error)' : 'inherit' }}>
              {stats.totalAccessDenied}
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Blocked unauthorized attempts</div>
          </div>

          <div className="card">
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Failed Logins</span>
            <div style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0.25rem 0', color: stats.totalFailedLogins > 0 ? 'var(--status-warning)' : 'inherit' }}>
              {stats.totalFailedLogins}
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Rate-limited failures</div>
          </div>
        </div>
      )}

      {/* Pending Registrations Section */}
      <div className="card" style={{ marginBottom: '2rem' }}>
        <div className="flex-between" style={{ marginBottom: '1.25rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Clock size={20} style={{ color: 'var(--status-warning)' }} />
              <h3 style={{ margin: 0 }}>Pending Registrations</h3>
              {pendingUsers.length > 0 && (
                <span
                  style={{
                    backgroundColor: 'var(--status-warning)',
                    color: '#000',
                    fontWeight: 700,
                    borderRadius: '999px',
                    padding: '0.15rem 0.55rem',
                    fontSize: '0.75rem',
                  }}
                >
                  {pendingUsers.length} awaiting review
                </span>
              )}
            </div>
            <p style={{ fontSize: '0.82rem', marginTop: '0.25rem' }}>
              Public registration requests must be reviewed and approved by an administrator before users can authenticate.
            </p>
          </div>
        </div>

        {pendingUsers.length === 0 ? (
          <div
            style={{
              padding: '2.5rem 1rem',
              textAlign: 'center',
              backgroundColor: 'var(--bg-app)',
              borderRadius: 'var(--radius-sm)',
              border: '1px dashed var(--border-color)',
            }}
          >
            <CheckCircle2 size={32} style={{ color: 'var(--brand-highlight)', marginBottom: '0.5rem' }} />
            <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>All caught up!</div>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
              There are no pending registration requests awaiting approval at this time.
            </div>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table">
              <thead>
                <tr>
                  <th>Requested By</th>
                  <th>Email</th>
                  <th>Submitted At</th>
                  <th>Assigned Role</th>
                  <th style={{ textAlign: 'right' }}>Review Actions</th>
                </tr>
              </thead>
              <tbody>
                {pendingUsers.map((p) => {
                  const isProcessing = actionInProgressId === p.id;
                  const selectedRole = roleSelectionMap[p.id] || 'VIEWER';
                  return (
                    <tr key={p.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{p.fullName}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>@{p.username}</div>
                      </td>
                      <td style={{ fontSize: '0.85rem' }}>{p.email}</td>
                      <td style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                        {new Date(p.createdAt).toLocaleString()}
                      </td>
                      <td>
                        <select
                          className="form-select"
                          style={{ fontSize: '0.8rem', padding: '0.35rem 0.6rem', width: 'auto', minWidth: '120px' }}
                          value={selectedRole}
                          onChange={(e) => setRoleSelectionMap((prev) => ({ ...prev, [p.id]: e.target.value }))}
                          disabled={isProcessing}
                        >
                          <option value="VIEWER">VIEWER (Default)</option>
                          <option value="OWNER">OWNER</option>
                        </select>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                          <button
                            type="button"
                            className="btn btn-sm btn-primary"
                            style={{
                              fontSize: '0.75rem',
                              padding: '0.35rem 0.75rem',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.3rem',
                            }}
                            onClick={() => handleApproveRegistration(p)}
                            disabled={isProcessing}
                          >
                            <Check size={14} />
                            <span>{isProcessing ? 'Processing...' : 'Approve'}</span>
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-outline"
                            style={{
                              fontSize: '0.75rem',
                              padding: '0.35rem 0.75rem',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.3rem',
                              borderColor: 'var(--status-error)',
                              color: 'var(--status-error)',
                            }}
                            onClick={() => setRejectUser(p)}
                            disabled={isProcessing}
                          >
                            <X size={14} />
                            <span>Reject</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Users Management Table */}
      <div className="card" style={{ marginBottom: '2rem' }}>
        <div className="flex-between" style={{ marginBottom: '1.25rem', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h3>User Directory & Account Status</h3>
            <p style={{ fontSize: '0.82rem' }}>Deactivating an account instantly cuts off session and document access</p>
          </div>
        </div>

        {/* Search & Filter Toolbar */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem', marginBottom: '1.25rem' }}>
          <div style={{ position: 'relative' }}>
            <input
              type="text"
              className="form-input"
              style={{ paddingLeft: '2.3rem', fontSize: '0.85rem' }}
              placeholder="Search user, email, or name..."
              value={userSearch}
              onChange={(e) => setUserSearch(e.target.value)}
            />
            <Search
              size={15}
              style={{
                position: 'absolute',
                left: '0.75rem',
                top: '50%',
                transform: 'translateY(-50%)',
                color: 'var(--text-muted)',
              }}
            />
          </div>

          <div>
            <select
              className="form-select"
              style={{ fontSize: '0.85rem' }}
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
            >
              <option value="">All Roles</option>
              <option value="ADMIN">ADMIN</option>
              <option value="OWNER">OWNER</option>
              <option value="VIEWER">VIEWER</option>
            </select>
          </div>

          <div>
            <select
              className="form-select"
              style={{ fontSize: '0.85rem' }}
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="">All Statuses</option>
              <option value="ACTIVE">Active Accounts</option>
              <option value="DEACTIVATED">Deactivated Accounts</option>
            </select>
          </div>
        </div>

        {loading ? (
          <div style={{ padding: '2rem', textAlign: 'center' }}>Loading user registry...</div>
        ) : (
          <div className="table-responsive">
            <table className="table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users
                  .filter((u) => {
                    const q = userSearch.toLowerCase().trim();
                    const matchesSearch = !q ||
                      (u.fullName && u.fullName.toLowerCase().includes(q)) ||
                      (u.username && u.username.toLowerCase().includes(q)) ||
                      (u.email && u.email.toLowerCase().includes(q));
                    const matchesRole = !roleFilter || u.role === roleFilter;
                    const matchesStatus = !statusFilter ||
                      (statusFilter === 'ACTIVE' && u.isActive) ||
                      (statusFilter === 'DEACTIVATED' && !u.isActive);
                    return matchesSearch && matchesRole && matchesStatus;
                  })
                  .map((u) => {
                    const isSelf = currentUser?.id === u.id || currentUser?.username === u.username;
                    return (
                      <tr key={u.id}>
                        <td>
                          <div style={{ fontWeight: 600 }}>
                            {u.fullName} {isSelf && <span style={{ fontSize: '0.7rem', color: 'var(--brand-highlight)', fontWeight: 500 }}>(You)</span>}
                          </div>
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>@{u.username}</div>
                        </td>
                        <td style={{ fontSize: '0.85rem' }}>{u.email}</td>
                        <td>
                          <Badge variant={getRoleBadgeVariant(u.role)}>{u.role}</Badge>
                        </td>
                        <td>{getStatusBadge(u)}</td>
                        <td style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                          {new Date(u.createdAt).toLocaleDateString()}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          {isSelf ? (
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontStyle: 'italic', padding: '0.25rem 0.5rem' }}>
                              Current Admin
                            </span>
                          ) : (
                            <button
                              className={`btn btn-sm ${u.isActive ? 'btn-outline' : 'btn-primary'}`}
                              style={{ fontSize: '0.75rem', padding: '0.25rem 0.6rem' }}
                              onClick={() => setStatusUser(u)}
                            >
                              {u.isActive ? 'Deactivate' : 'Activate'}
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Provision User Modal */}
      <Modal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} title="Provision New User Account">
        <form onSubmit={handleCreateUser}>
          {createError && (
            <div className="alert alert-error">
              <div>{createError}</div>
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Full Name</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g. David Miller"
              value={newFullName}
              onChange={(e) => setNewFullName(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g. dmiller"
              value={newUsername}
              onChange={(e) => setNewUsername(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input
              type="email"
              className="form-input"
              placeholder="e.g. dmiller@trustvault.local"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Initial Password</label>
            <input
              type="password"
              className="form-input"
              placeholder="Min 8 characters"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              minLength={8}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Role</label>
            <select
              className="form-select"
              value={newRole}
              onChange={(e) => setNewRole(e.target.value)}
            >
              <option value="VIEWER">VIEWER (Read & download explicitly shared files only)</option>
              <option value="OWNER">OWNER (Upload, manage, and share documents)</option>
              <option value="ADMIN">ADMIN (System administration & audit oversight)</option>
            </select>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsCreateOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={createLoading}>
              {createLoading ? 'Provisioning...' : 'Provision User'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Confirm Status Toggle Dialog */}
      <ConfirmDialog
        isOpen={!!statusUser}
        onClose={() => setStatusUser(null)}
        onConfirm={handleToggleUserStatus}
        title={statusUser?.isActive ? 'Deactivate User Account' : 'Activate User Account'}
        message={
          statusUser?.isActive
            ? `Are you sure you want to deactivate ${statusUser?.fullName} (@${statusUser?.username})? They will be immediately blocked from making protected requests and accessing documents.`
            : `Are you sure you want to activate ${statusUser?.fullName} (@${statusUser?.username})? They will regain normal access according to their role.`
        }
        confirmText={statusUser?.isActive ? 'Deactivate Account' : 'Activate Account'}
        danger={statusUser?.isActive}
        loading={statusActionLoading}
      />

      {/* Confirm Reject Registration Dialog */}
      <ConfirmDialog
        isOpen={!!rejectUser}
        onClose={() => setRejectUser(null)}
        onConfirm={handleConfirmReject}
        title="Reject Registration Request"
        message={`Are you sure you want to reject the registration request from ${rejectUser?.fullName} (${rejectUser?.email})? This action cannot be undone.`}
        confirmText="Reject Registration"
        danger
        loading={actionInProgressId === rejectUser?.id}
      />
    </div>
  );
};
