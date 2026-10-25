import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { ShieldCheck, User, Mail, Lock, CheckCircle2, AlertCircle, ArrowLeft } from 'lucide-react';
import { ThemeToggle } from '../components/ThemeToggle';

export const RegisterPage = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    password: '',
    confirmPassword: '',
  });

  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [loading, setLoading] = useState(false);
  const [registeredSuccess, setRegisteredSuccess] = useState(false);

  const validate = () => {
    const errs = {};
    if (!formData.fullName.trim()) {
      errs.fullName = 'Full name is required';
    } else if (formData.fullName.trim().length > 100) {
      errs.fullName = 'Full name must not exceed 100 characters';
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!formData.email.trim()) {
      errs.email = 'Email address is required';
    } else if (!emailRegex.test(formData.email.trim())) {
      errs.email = 'Please enter a valid email address';
    }

    if (!formData.password) {
      errs.password = 'Password is required';
    } else if (formData.password.length < 8) {
      errs.password = 'Password must be at least 8 characters long';
    }

    if (!formData.confirmPassword) {
      errs.confirmPassword = 'Confirm password is required';
    } else if (formData.password !== formData.confirmPassword) {
      errs.confirmPassword = 'Passwords do not match';
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: '' }));
    }
    if (serverError) {
      setServerError('');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;

    setServerError('');
    setLoading(true);

    try {
      await api.post('/auth/register', {
        fullName: formData.fullName.trim(),
        email: formData.email.trim().toLowerCase(),
        password: formData.password,
        confirmPassword: formData.confirmPassword,
      });

      setRegisteredSuccess(true);
    } catch (err) {
      setServerError(err.message || 'Failed to submit registration request');
    } finally {
      setLoading(false);
    }
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

      <div style={{ maxWidth: '480px', width: '100%' }}>
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
            Request an Account — Requires Administrator Approval
          </p>
        </div>

        <div className="card" style={{ padding: '2rem' }}>
          {registeredSuccess ? (
            <div style={{ textAlign: 'center', padding: '1rem 0' }}>
              <div
                style={{
                  width: '64px',
                  height: '64px',
                  backgroundColor: 'var(--status-success-bg)',
                  borderRadius: '50%',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--status-success)',
                  marginBottom: '1.25rem',
                }}
              >
                <CheckCircle2 size={36} />
              </div>
              <h2 style={{ fontSize: '1.25rem', marginBottom: '0.75rem' }}>
                Registration Submitted
              </h2>
              <p
                style={{
                  fontSize: '0.925rem',
                  lineHeight: '1.5',
                  color: 'var(--text-secondary)',
                  marginBottom: '1.75rem',
                }}
              >
                Your registration request has been recorded securely in the audit log.
                To preserve vault confidentiality, your account is currently <strong>PENDING</strong> administrator review.
                Once approved, you will be able to sign in with your credentials.
              </p>
              <button
                type="button"
                className="btn btn-primary"
                style={{ width: '100%', padding: '0.75rem' }}
                onClick={() => navigate('/login', { state: { message: 'Registration submitted. Please await admin approval before signing in.' } })}
              >
                Return to Sign In
              </button>
            </div>
          ) : (
            <>
              {serverError && (
                <div className="alert alert-error">
                  <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
                  <div>{serverError}</div>
                </div>
              )}

              <form onSubmit={handleSubmit} noValidate>
                <div className="form-group">
                  <label className="form-label" htmlFor="fullName">
                    Full Name <span style={{ color: 'var(--status-error)' }}>*</span>
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      id="fullName"
                      name="fullName"
                      type="text"
                      className="form-input"
                      style={{ paddingLeft: '2.5rem' }}
                      placeholder="e.g. Eleanor Vance"
                      value={formData.fullName}
                      onChange={handleChange}
                      disabled={loading}
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
                  {errors.fullName && (
                    <div style={{ color: 'var(--status-error)', fontSize: '0.8rem', marginTop: '0.3rem' }}>
                      {errors.fullName}
                    </div>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="email">
                    Email Address <span style={{ color: 'var(--status-error)' }}>*</span>
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      id="email"
                      name="email"
                      type="email"
                      className="form-input"
                      style={{ paddingLeft: '2.5rem' }}
                      placeholder="name@company.com"
                      value={formData.email}
                      onChange={handleChange}
                      disabled={loading}
                      required
                    />
                    <Mail
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
                  {errors.email && (
                    <div style={{ color: 'var(--status-error)', fontSize: '0.8rem', marginTop: '0.3rem' }}>
                      {errors.email}
                    </div>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="password">
                    Password <span style={{ color: 'var(--status-error)' }}>*</span>
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      id="password"
                      name="password"
                      type="password"
                      className="form-input"
                      style={{ paddingLeft: '2.5rem' }}
                      placeholder="At least 8 characters"
                      value={formData.password}
                      onChange={handleChange}
                      disabled={loading}
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
                  {errors.password && (
                    <div style={{ color: 'var(--status-error)', fontSize: '0.8rem', marginTop: '0.3rem' }}>
                      {errors.password}
                    </div>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="confirmPassword">
                    Confirm Password <span style={{ color: 'var(--status-error)' }}>*</span>
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      id="confirmPassword"
                      name="confirmPassword"
                      type="password"
                      className="form-input"
                      style={{ paddingLeft: '2.5rem' }}
                      placeholder="Re-enter your password"
                      value={formData.confirmPassword}
                      onChange={handleChange}
                      disabled={loading}
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
                  {errors.confirmPassword && (
                    <div style={{ color: 'var(--status-error)', fontSize: '0.8rem', marginTop: '0.3rem' }}>
                      {errors.confirmPassword}
                    </div>
                  )}
                </div>

                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{ width: '100%', padding: '0.75rem', marginTop: '0.5rem' }}
                  disabled={loading}
                >
                  {loading ? 'Submitting Registration...' : 'Submit Registration Request'}
                </button>
              </form>

              <div
                style={{
                  marginTop: '1.5rem',
                  paddingTop: '1.25rem',
                  borderTop: '1px solid var(--border-color)',
                  textAlign: 'center',
                  fontSize: '0.875rem',
                  color: 'var(--text-secondary)',
                }}
              >
                Already have an account?{' '}
                <Link
                  to="/login"
                  style={{
                    color: 'var(--brand-highlight)',
                    fontWeight: 600,
                    textDecoration: 'none',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '0.25rem',
                  }}
                >
                  Sign In
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
