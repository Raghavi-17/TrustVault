import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  // Initialize CSRF and verify session
  const checkAuth = useCallback(async () => {
    try {
      setLoading(true);
      // Fetch CSRF token cookie first
      await api.get('/auth/csrf').catch(() => {});
      const res = await api.get('/auth/me');
      if (res && res.data) {
        setUser(res.data);
      } else {
        setUser(null);
      }
    } catch (err) {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  const login = async (username, password) => {
    // Ensure fresh CSRF cookie before login
    await api.get('/auth/csrf').catch(() => {});
    const res = await api.post('/auth/login', { username, password });
    if (res && res.data) {
      setUser(res.data);
      setSessionExpired(false);
      return res.data;
    }
    throw new Error('Login failed: invalid response');
  };

  const logout = async () => {
    try {
      await api.post('/auth/logout', {});
    } catch (err) {
      console.warn('Logout error', err);
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isAdmin: user?.role === 'ADMIN',
        isOwner: user?.role === 'OWNER',
        isViewer: user?.role === 'VIEWER',
        loading,
        sessionExpired,
        setSessionExpired,
        login,
        logout,
        refreshUser: checkAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
