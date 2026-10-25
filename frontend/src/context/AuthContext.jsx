
import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
} from 'react';

import { api, setCsrfToken } from '../api/client';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  // Fetch CSRF token before checking the current session.
  const initializeCsrf = useCallback(async () => {
    try {
      const response = await api.get('/auth/csrf');

      // The API client returns the parsed response directly.
      const token = response?.token ?? response?.data?.token;

      if (token) {
        setCsrfToken(token);
      }

      return token ?? null;
    } catch (error) {
      console.error('Unable to initialize CSRF token:', error);
      return null;
    }
  }, []);

  // Initialize CSRF and verify the current session.
  const checkAuth = useCallback(async () => {
    setLoading(true);

    try {
      await initializeCsrf();

      const response = await api.get('/auth/me');
      const authenticatedUser = response?.data ?? response?.user ?? null;

      setUser(authenticatedUser);
    } catch (error) {
      setUser(null);

      if (error?.status === 401 || error?.status === 403) {
        setSessionExpired(true);
      }

      console.error('Unable to verify authentication:', error);
    } finally {
      setLoading(false);
    }
  }, [initializeCsrf]);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  const login = async (username, password) => {
    await initializeCsrf();

    const response = await api.post('/auth/login', {
      username,
      password,
    });

    const authenticatedUser =
      response?.data ?? response?.user ?? null;

    if (!authenticatedUser) {
      throw new Error('Login failed: invalid response');
    }

    setUser(authenticatedUser);
    setSessionExpired(false);

    return authenticatedUser;
  };

  const logout = async () => {
    try {
      await initializeCsrf();
      await api.post('/auth/logout', {});
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      setUser(null);
      setSessionExpired(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isAdmin:
          user?.role === 'ADMIN' || user?.role === 'ROLE_ADMIN',
        isOwner:
          user?.role === 'OWNER' || user?.role === 'ROLE_OWNER',
        isViewer:
          user?.role === 'VIEWER' || user?.role === 'ROLE_VIEWER',
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

