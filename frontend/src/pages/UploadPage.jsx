import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import { useNavigate } from 'react-router-dom';
import { UploadCloud, File, AlertCircle, CheckCircle, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';

export const UploadPage = () => {
  const navigate = useNavigate();

  const [file, setFile] = useState(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [tags, setTags] = useState('');
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [isDragOver, setIsDragOver] = useState(false);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      const res = await api.get('/categories');
      if (res?.data) setCategories(res.data);
    } catch (err) {
      console.error('Failed to load categories', err);
    }
  };

  const handleFileSelection = (selectedFile) => {
    if (!selectedFile) return;

    // Check size limit: 15MB
    if (selectedFile.size > 15 * 1024 * 1024) {
      setError(`File size exceeds 15MB limit (${(selectedFile.size / (1024 * 1024)).toFixed(1)} MB)`);
      setFile(null);
      return;
    }

    setError('');
    setFile(selectedFile);
    if (!title) {
      // Default title to filename without extension
      const nameWithoutExt = selectedFile.name.replace(/\.[^/.]+$/, '');
      setTitle(nameWithoutExt);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) {
      setError('Please select a file to upload');
      return;
    }
    if (!title.trim()) {
      setError('Please provide a document title');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('title', title.trim());
      if (description) formData.append('description', description.trim());
      if (categoryId) formData.append('categoryId', categoryId);
      if (tags) formData.append('tags', tags.trim());

      await api.upload('/documents', formData);
      navigate('/documents');
    } catch (err) {
      setError(err.message || 'Failed to upload document');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-body" style={{ maxWidth: '780px' }}>
      <div style={{ marginBottom: '1.5rem' }}>
        <Link to="/documents" className="btn btn-outline btn-sm" style={{ marginBottom: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}>
          <ArrowLeft size={14} />
          <span>Back to Documents</span>
        </Link>
        <h1>Upload Secure Document</h1>
        <p>Uploaded files are validated, stored in private storage, and indexed into the tamper-evident audit trail</p>
      </div>

      <div className="card" style={{ padding: '2rem' }}>
        {error && (
          <div className="alert alert-error">
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{error}</div>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {/* File Dropzone */}
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setIsDragOver(true);
            }}
            onDragLeave={() => setIsDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setIsDragOver(false);
              if (e.dataTransfer.files && e.dataTransfer.files[0]) {
                handleFileSelection(e.dataTransfer.files[0]);
              }
            }}
            style={{
              border: `2px dashed ${isDragOver ? 'var(--brand-primary)' : 'var(--border-color)'}`,
              borderRadius: 'var(--radius-md)',
              padding: '2.5rem 1.5rem',
              textAlign: 'center',
              backgroundColor: isDragOver ? 'var(--brand-light)' : 'var(--bg-surface)',
              cursor: 'pointer',
              transition: 'var(--transition)',
              marginBottom: '1.75rem',
            }}
            onClick={() => document.getElementById('file-upload-input').click()}
          >
            <input
              id="file-upload-input"
              type="file"
              style={{ display: 'none' }}
              onChange={(e) => handleFileSelection(e.target.files[0])}
            />

            {file ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.5rem' }}>
                <CheckCircle size={36} style={{ color: 'var(--status-success)' }} />
                <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{file.name}</div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                  {(file.size / 1024).toFixed(1)} KB — Ready to upload
                </div>
                <span style={{ fontSize: '0.75rem', color: 'var(--brand-highlight)', textDecoration: 'underline', marginTop: '0.25rem' }}>
                  Click or drag to choose a different file
                </span>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.5rem' }}>
                <UploadCloud size={40} style={{ color: 'var(--brand-highlight)' }} />
                <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                  Choose a file or drag & drop it here
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                  Supported formats: PDF, DOCX, DOC, TXT, CSV, XLSX, XLS, PNG, JPG, JPEG, MD (Max 15MB)
                </div>
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="doc-title">
              Document Title <span style={{ color: 'var(--status-error)' }}>*</span>
            </label>
            <input
              id="doc-title"
              type="text"
              className="form-input"
              placeholder="e.g., Q3 2026 Audit Report"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="doc-desc">
              Description
            </label>
            <textarea
              id="doc-desc"
              className="form-textarea"
              rows="3"
              placeholder="Brief summary of document contents and purpose..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="doc-category">
                Category
              </label>
              <select
                id="doc-category"
                className="form-select"
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
              >
                <option value="">General (Uncategorized)</option>
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="doc-tags">
                Tags (comma separated)
              </label>
              <input
                id="doc-tags"
                type="text"
                className="form-input"
                placeholder="finance, audit, confidential"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
              />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.75rem' }}>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => navigate('/documents')}
              disabled={loading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading || !file}
            >
              {loading ? 'Validating & Uploading...' : 'Upload Document'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
