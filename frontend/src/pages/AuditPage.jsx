import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import { 
  ShieldCheck, ShieldAlert, CheckCircle, AlertOctagon, 
  RefreshCw, Filter, ChevronLeft, ChevronRight, Hash, Clock
} from 'lucide-react';
import { Badge } from '../components/Badge';

export const AuditPage = () => {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Filters
  const [outcomeFilter, setOutcomeFilter] = useState('');
  const [actionFilter, setActionFilter] = useState('');

  // Integrity Verification state
  const [verificationResult, setVerificationResult] = useState(null);
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    fetchEvents();
  }, [currentPage, outcomeFilter, actionFilter]);

  const fetchEvents = async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({
        page: currentPage,
        size: 15,
      });
      if (outcomeFilter) params.append('outcome', outcomeFilter);
      if (actionFilter) params.append('action', actionFilter);

      const res = await api.get(`/audit/events?${params}`);
      if (res?.data) {
        setEvents(res.data.content || []);
        setTotalPages(res.data.totalPages || 0);
        setTotalElements(res.data.totalElements || 0);
      }
    } catch (err) {
      console.error('Failed to load audit events', err);
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyIntegrity = async () => {
    setVerifying(true);
    try {
      const res = await api.post('/audit/verify', {});
      if (res?.data) {
        setVerificationResult(res.data);
      }
    } catch (err) {
      alert(err.message || 'Integrity check failed');
    } finally {
      setVerifying(false);
    }
  };

  const getActionBadgeVariant = (action) => {
    if (action.includes('DENIED') || action.includes('FAILURE')) return 'error';
    if (action.includes('REVOKE') || action.includes('DELETE')) return 'warning';
    if (action.includes('UPLOAD') || action.includes('ACTIVATE') || action.includes('GRANT')) return 'success';
    return 'info';
  };

  const getOutcomeBadge = (outcome) => {
    switch (outcome) {
      case 'SUCCESS': return <Badge variant="success">SUCCESS</Badge>;
      case 'DENIED': return <Badge variant="error">DENIED</Badge>;
      case 'FAILED': return <Badge variant="error">FAILED</Badge>;
      default: return <Badge variant="neutral">{outcome}</Badge>;
    }
  };

  return (
    <div className="page-body">
      <div className="flex-between" style={{ marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1>Tamper-Evident Audit Trail</h1>
          <p>Cryptographically linked SHA-256 hash chain recording every vault operation</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            className="btn btn-primary"
            onClick={handleVerifyIntegrity}
            disabled={verifying}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
          >
            <ShieldCheck size={18} />
            <span>{verifying ? 'Recalculating SHA-256 Chain...' : 'Verify Cryptographic Integrity'}</span>
          </button>
        </div>
      </div>

      {/* Verification Banner */}
      {verificationResult && (
        <div
          className={`alert alert-${verificationResult.isChainValid ? 'success' : 'error'}`}
          style={{ padding: '1.25rem', marginBottom: '1.75rem' }}
        >
          {verificationResult.isChainValid ? (
            <CheckCircle size={24} style={{ flexShrink: 0, marginTop: '2px' }} />
          ) : (
            <AlertOctagon size={24} style={{ flexShrink: 0, marginTop: '2px' }} />
          )}
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: '1rem', marginBottom: '0.2rem' }}>
              {verificationResult.isChainValid ? 'Cryptographic Integrity Verified' : 'Audit Chain Tampering Detected!'}
            </div>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'inherit' }}>
              {verificationResult.message}
            </p>
            <div style={{ fontSize: '0.75rem', marginTop: '0.4rem', opacity: 0.85 }}>
              Verified: {new Date(verificationResult.verifiedAt).toLocaleString()} | Blocks Validated: {verificationResult.totalEventsChecked}
            </div>
          </div>
        </div>
      )}

      {/* Filter Toolbar */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem' }}>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ minWidth: '180px' }}>
            <select
              className="form-select"
              value={outcomeFilter}
              onChange={(e) => {
                setOutcomeFilter(e.target.value);
                setCurrentPage(0);
              }}
            >
              <option value="">All Outcomes</option>
              <option value="SUCCESS">Success Only</option>
              <option value="DENIED">Denied / Blocked Only</option>
              <option value="FAILED">Failed Only</option>
            </select>
          </div>

          <div style={{ minWidth: '220px' }}>
            <select
              className="form-select"
              value={actionFilter}
              onChange={(e) => {
                setActionFilter(e.target.value);
                setCurrentPage(0);
              }}
            >
              <option value="">All Action Types</option>
              <option value="DOCUMENT_UPLOAD">Document Upload</option>
              <option value="DOCUMENT_READ">Document Read</option>
              <option value="DOCUMENT_DOWNLOAD">Document Download</option>
              <option value="ACCESS_DENIED">Access Denied</option>
              <option value="PERMISSION_GRANT">Permission Grant</option>
              <option value="PERMISSION_REVOKE">Permission Revoke</option>
              <option value="LOGIN_SUCCESS">Login Success</option>
              <option value="LOGIN_FAILURE">Login Failure</option>
              <option value="ACCOUNT_DEACTIVATE">Account Deactivated</option>
              <option value="AUDIT_INTEGRITY_VERIFY">Integrity Verification</option>
            </select>
          </div>

          <button className="btn btn-secondary btn-sm" onClick={fetchEvents} style={{ marginLeft: 'auto' }}>
            <RefreshCw size={14} />
            <span>Refresh Events</span>
          </button>
        </div>
      </div>

      {/* Events Table */}
      <div className="card">
        {loading ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
            Scanning audit block entries...
          </div>
        ) : events.length === 0 ? (
          <div style={{ padding: '3rem 1rem', textAlign: 'center' }}>
            <h4>No audit records found</h4>
            <p>Adjust filter settings to view more entries.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table" style={{ fontSize: '0.82rem' }}>
              <thead>
                <tr>
                  <th>Block #</th>
                  <th>Timestamp (UTC)</th>
                  <th>Actor</th>
                  <th>Action</th>
                  <th>Target</th>
                  <th>Outcome</th>
                  <th>Details</th>
                  <th>Block SHA-256 Hash</th>
                </tr>
              </thead>
              <tbody>
                {events.map((evt) => (
                  <tr key={evt.id}>
                    <td>
                      <span className="badge badge-neutral" style={{ fontWeight: 700 }}>
                        #{evt.sequenceNumber}
                      </span>
                    </td>
                    <td style={{ whiteSpace: 'nowrap', color: 'var(--text-secondary)' }}>
                      {new Date(evt.timestamp).toLocaleString()}
                    </td>
                    <td>
                      <span style={{ fontWeight: 600 }}>{evt.actorUsername || 'ANONYMOUS'}</span>
                      {evt.ipAddress && (
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{evt.ipAddress}</div>
                      )}
                    </td>
                    <td>
                      <Badge variant={getActionBadgeVariant(evt.action)}>
                        {evt.action}
                      </Badge>
                    </td>
                    <td>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>{evt.targetType}</span>
                      {evt.targetId && (
                        <div className="font-mono" style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                          {evt.targetId.substring(0, 8)}...
                        </div>
                      )}
                    </td>
                    <td>{getOutcomeBadge(evt.outcome)}</td>
                    <td style={{ maxWidth: '280px' }}>
                      <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={evt.details}>
                        {evt.details}
                      </div>
                    </td>
                    <td>
                      <div
                        className="font-mono"
                        style={{
                          fontSize: '0.68rem',
                          background: 'var(--bg-surface)',
                          padding: '0.2rem 0.4rem',
                          borderRadius: 'var(--radius-sm)',
                          display: 'inline-block',
                          maxWidth: '120px',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                        title={`Current: ${evt.currentHash}\nPrevious: ${evt.previousHash}`}
                      >
                        {evt.currentHash.substring(0, 12)}...
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex-between" style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid var(--border-color)' }}>
            <span style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
              Total {totalElements} audit entries (Page {currentPage + 1} of {totalPages})
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
    </div>
  );
};
