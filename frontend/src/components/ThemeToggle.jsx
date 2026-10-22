import React from 'react';
import { useTheme } from '../context/ThemeContext';
import { Sun, Moon } from 'lucide-react';

export const ThemeToggle = () => {
  const { theme, toggleTheme, isDark } = useTheme();

  return (
    <button
      onClick={toggleTheme}
      className="btn btn-secondary btn-sm"
      style={{
        borderRadius: '9999px',
        padding: '0.45rem',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: '36px',
        height: '36px',
      }}
      title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
      aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
    >
      {isDark ? (
        <Sun size={18} style={{ color: '#FBBF24' }} />
      ) : (
        <Moon size={18} style={{ color: '#4CAF7D' }} />
      )}
    </button>
  );
};
