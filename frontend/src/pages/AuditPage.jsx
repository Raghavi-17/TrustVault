import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import { 
  ShieldCheck, ShieldAlert, CheckCircle, AlertOctagon, AlertTriangle,
  RefreshCw, ChevronLeft, ChevronRight
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
  const [userFilter, setUserFilter] = useState('');
  const [documentFilter, setDocumentFilter] = useState('');
  const [startDateFilter, setStartDateFilter] = useState('');
  const [endDateFilter, setEndDateFilter] = useState('');

  // Integrity Verification state
  const [verificationResult, setVerificationResult] = useState(null);
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    fetchEvents();
  }, [currentPage, outcomeFilter, actionFilter, userFilter, documentFilter, startDateFilter, endDateFilter]);

  useEffect(() => {
    checkInitialVerification();
  }, []);

  const checkInitialVerification = async () => {
    try {
      const res = await api.get('/audit/verify');
      if (res?.data) {
        setVerificationResult(res.data);
      }
    } catch (err) {
      console.warn('Initial audit verification check failed:', err);
    }
  };

  const fetchEvents = async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({
        page: currentPage,
        size: 15,
      });
      if (outcomeFilter) params.append('outcome', outcomeFilter);
      if (actionFilter) params.append('action', actionFilter);
      if (userFilter.trim()) params.append('user', userFilter.trim());
      if (documentFilter.trim()) params.append('document', documentFilter.trim());
      if (startDateFilter) params.append('startDate', new Date(startDateFilter).toISOString());
      if (endDateFilter) {
        const end = new Date(endDateFilter);
        end.setHours(23, 59, 59, 999);
        params.append('endDate', end.toISOString());
      }

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

  const handleResetFilters = () => {
    setOutcomeFilter('');
    setActionFilter('');
    setUserFilter('');
    setDocumentFilter('');
    setStartDateFilter('');
    setEndDateFilter('');
    setCurrentPage(0);
  };

  const handleVerifyIntegrity = async () => {
    setVerifying(true);
    try {
      const res = await api.post('/audit/verify', {});
      if (res?.data) {
        setVerificationResult(res.data);
        // Refresh event list to show the new verification event
        fetchEvents();
      }
    } catch (err) {
      setVerificationResult({
        status: 'VERIFICATION_ERROR',
        isChainValid: false,
        totalEventsChecked: 0,
        brokenSequenceNumber: null,
        message: err.message || 'Integrity check failed to execute',
        verifiedAt: new Date().toISOString(),
      });
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

  const isIntact = verificationResult && verificationResult.isChainValid === true;
  const isInvalid = verificationResult && verificationResult.isChainValid === false && verificationResult.status !== 'VERIFICATION_ERROR';
  const isError = verificationResult && (verificationResult.status === 'VERIFICATION_ERROR' || (verificationResult.verificationStatus === 'VERIFICATION FAILED'));

  const statusLabel = isIntact
    ? 'VERIFIED'
    : isInvalid
    ? 'ISSUES DETECTED'
    : isError
    ? 'VERIFICATION FAILED'
    : 'UNVERIFIED';

  const errorsCount = isIntact ? 0 : (verificationResult?.integrityErrorsDetected ?? (isInvalid || isError ? 1 : 0));
  const affectedBlock = verificationResult?.affectedEventIds && verificationResult.affectedEventIds.length > 0
    ? verificationResult.affectedEventIds.map(id => `#${id}`).join(', ')
    : verificationResult?.brokenSequenceNumber
    ? `#${verificationResult.brokenSequenceNumber}`
    : null;

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

      {/* Verification In-Progress Indicator */}
      {verifying && (
        <div className="alert alert-info" style={{ padding: '1rem 1.25rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} />
          <div>
            <strong>Verification In Progress:</strong> Recalculating SHA-256 canonical event hashes and validating chain links from genesis...
          </div>
        </div>
      )}

      {/* Verification Overview Metrics Grid */}
      {verificationResult && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
          <div className="card" style={{ padding: '1rem 1.25rem' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600 }}>CURRENT STATUS</span>
            <div style={{
              fontSize: '1.25rem',
              fontWeight: 800,
              marginTop: '0.35rem',
              color: isIntact ? 'var(--status-success)' : isInvalid ? 'var(--status-error)' : 'var(--status-warning, #D97706)'
            }}>
              {statusLabel}
            </div>
            <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              {isIntact ? 'All records passed verification' : isInvalid ? 'Integrity inconsistency found' : 'Check could not complete'}
            </span>
          </div>

          <div className="card" style={{ padding: '1rem 1.25rem' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600 }}>TOTAL EVENTS CHECKED</span>
            <div style={{ fontSize: '1.25rem', fontWeight: 800, marginTop: '0.35rem' }}>
              {verificationResult.totalEventsChecked}
            </div>
            <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>Blocks validated from genesis</span>
          </div>

          <div className="card" style={{ padding: '1rem 1.25rem' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600 }}>INTEGRITY ERRORS</span>
            <div style={{
              fontSize: '1.25rem',
              fontWeight: 800,
              marginTop: '0.35rem',
              color: errorsCount === 0 ? 'var(--status-success)' : 'var(--status-error)'
            }}>
              {errorsCount}
            </div>
            <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              {errorsCount === 0 ? 'Zero tampering detected' : 'Inconsistencies detected'}
            </span>
          </div>

          <div className="card" style={{ padding: '1rem 1.25rem' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600 }}>LAST SUCCESSFUL VERIFICATION</span>
            <div style={{ fontSize: '0.92rem', fontWeight: 700, marginTop: '0.5rem', wordBreak: 'break-word' }}>
              {verificationResult.lastSuccessfulVerification
                ? new Date(verificationResult.lastSuccessfulVerification).toLocaleString()
                : isIntact && verificationResult.verifiedAt
                ? new Date(verificationResult.verifiedAt).toLocaleString()
                : 'None recorded'}
            </div>
            <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>Verified timestamp</span>
          </div>
        </div>
      )}

      {/* Verification Detailed Banner */}
      {verificationResult && (
        <div
          className={`alert alert-${isIntact ? 'success' : isInvalid ? 'error' : 'warning'}`}
          style={{ padding: '1.25rem', marginBottom: '1.75rem' }}
        >
          {isIntact ? (
            <CheckCircle size={24} style={{ flexShrink: 0, marginTop: '2px' }} />
          ) : isInvalid ? (
            <AlertOctagon size={24} style={{ flexShrink: 0, marginTop: '2px' }} />
          ) : (
            <AlertTriangle size={24} style={{ flexShrink: 0, marginTop: '2px' }} />
          )}
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: '1rem', marginBottom: '0.2rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span>{statusLabel}</span>
              <span style={{ fontSize: '0.85rem', fontWeight: 500, opacity: 0.9 }}>
                — {isIntact ? 'The checked audit records passed verification' : isInvalid ? 'The verification process found inconsistencies' : 'The integrity check could not complete'}
              </span>
            </div>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'inherit' }}>
              {verificationResult.message}
            </p>
            {affectedBlock && (
              <div style={{ fontSize: '0.8rem', fontWeight: 600, marginTop: '0.4rem', color: 'var(--status-error)' }}>
                Affected Audit Block: {affectedBlock}
              </div>
            )}
            <div style={{ fontSize: '0.75rem', marginTop: '0.4rem', opacity: 0.85 }}>
              Verified: {new Date(verificationResult.verifiedAt).toLocaleString()} | Blocks Validated: {verificationResult.totalEventsChecked}
            </div>
          </div>
        </div>
      )}

      {/* Security Limitation & Signed Checkpoints Disclosure */}
      <div
        className="card"
        style={{
          padding: '0.9rem 1.25rem',
          marginBottom: '1.75rem',
          fontSize: '0.82rem',
          color: 'var(--text-secondary)',
          background: 'var(--bg-surface)',
          borderLeft: '3px solid var(--brand-primary)',
        }}
      >
        <strong style={{ color: 'var(--text-primary)' }}>Cryptographic Assurance Note:</strong>{' '}
        SHA-256 hash chaining ensures mathematical tamper evidence across audit blocks. While a single-database hash chain cannot prevent an attacker with full database administrator write privileges from rewriting the entire database, secondary signed checkpoints provide an independent verification baseline.
      </div>

      {/* Filter Toolbar */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem' }}>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ minWidth: '150px' }}>
            <input
              type="text"
              className="form-input"
              placeholder="Filter by user..."
              value={userFilter}
              onChange={(e) => {
                setUserFilter(e.target.value);
                setCurrentPage(0);
              }}
              style={{ fontSize: '0.85rem', padding: '0.45rem 0.65rem' }}
            />
          </div>

          <div style={{ minWidth: '160px' }}>
            <input
              type="text"
              className="form-input"
              placeholder="Filter by document..."
              value={documentFilter}
              onChange={(e) => {
                setDocumentFilter(e.target.value);
                setCurrentPage(0);
              }}
              style={{ fontSize: '0.85rem', padding: '0.45rem 0.65rem' }}
            />
          </div>

          <div style={{ minWidth: '135px' }}>
            <input
              type="date"
              className="form-input"
              title="Start Date"
              value={startDateFilter}
              onChange={(e) => {
                setStartDateFilter(e.target.value);
                setCurrentPage(0);
              }}
              style={{ fontSize: '0.85rem', padding: '0.45rem 0.65rem' }}
            />
          </div>

          <div style={{ minWidth: '135px' }}>
            <input
              type="date"
              className="form-input"
              title="End Date"
              value={endDateFilter}
              onChange={(e) => {
                setEndDateFilter(e.target.value);
                setCurrentPage(0);
              }}
              style={{ fontSize: '0.85rem', padding: '0.45rem 0.65rem' }}
            />
          </div>

          <div style={{ minWidth: '140px' }}>
            <select
              className="form-select"
              value={outcomeFilter}
              onChange={(e) => {
                setOutcomeFilter(e.target.value);
                setCurrentPage(0);
              }}
              style={{ fontSize: '0.85rem', padding: '0.45rem 0.65rem' }}
            >
              <option value="">All Outcomes</option>
              <option value="SUCCESS">Success Only</option>
              <option value="DENIED">Denied / Blocked Only</option>
              <option value="FAILED">Failed Only</option>
            </select>
          </div>

          <div style={{ minWidth: '170px' }}>
            <select
              className="form-select"
              value={actionFilter}
              onChange={(e) => {
                setActionFilter(e.target.value);
                setCurrentPage(0);
              }}
              style={{ fontSize: '0.85rem', padding: '0.45rem 0.65rem' }}
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
              <option value="CATEGORY_CREATE">Category Created</option>
              <option value="AUDIT_INTEGRITY_VERIFY">Integrity Verification</option>
              <option value="ADMIN_ACTION">Admin Actions</option>
            </select>
          </div>

          {(userFilter || documentFilter || startDateFilter || endDateFilter || outcomeFilter || actionFilter) && (
            <button
              className="btn btn-outline btn-sm"
              onClick={handleResetFilters}
              title="Clear all active filters"
            >
              Reset
            </button>
          )}

          <button className="btn btn-secondary btn-sm" onClick={fetchEvents} style={{ marginLeft: 'auto' }}>
            <RefreshCw size={14} />
            <span>Refresh</span>
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
