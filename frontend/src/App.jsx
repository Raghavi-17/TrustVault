import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { Sidebar } from './components/Sidebar';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { DocumentDetailPage } from './pages/DocumentDetailPage';
import { UploadPage } from './pages/UploadPage';
import { AdminPage } from './pages/AdminPage';
import { AuditPage } from './pages/AuditPage';
import { AccessDeniedPage } from './pages/AccessDeniedPage';
import { NotFoundPage } from './pages/NotFoundPage';

// Protected layout wrapper
const ProtectedLayout = ({ children, requiredRole = null }) => {
  const { isAuthenticated, loading, user } = useAuth();

  if (loading) {
    return (
      <div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-app)' }}>
        <p style={{ color: 'var(--text-secondary)' }}>Loading TrustVault...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (requiredRole && user?.role !== requiredRole && user?.role !== 'ADMIN') {
    return <Navigate to="/403" replace />;
  }

  return (
    <div className="app-container">
      <Sidebar />
      <div className="main-content">
        <Navbar />
        {children}
      </div>
    </div>
  );
};

export function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
              path="/dashboard"
              element={
                <ProtectedLayout>
                  <DashboardPage />
                </ProtectedLayout>
              }
            />

            <Route
              path="/documents"
              element={
                <ProtectedLayout>
                  <DocumentsPage />
                </ProtectedLayout>
              }
            />

            <Route
              path="/documents/:id"
              element={
                <ProtectedLayout>
                  <DocumentDetailPage />
                </ProtectedLayout>
              }
            />

            <Route
              path="/upload"
              element={
                <ProtectedLayout>
                  <UploadPage />
                </ProtectedLayout>
              }
            />

            <Route
              path="/admin"
              element={
                <ProtectedLayout requiredRole="ADMIN">
                  <AdminPage />
                </ProtectedLayout>
              }
            />

            <Route
              path="/audit"
              element={
                <ProtectedLayout requiredRole="ADMIN">
                  <AuditPage />
                </ProtectedLayout>
              }
            />

            <Route path="/403" element={<AccessDeniedPage />} />
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
