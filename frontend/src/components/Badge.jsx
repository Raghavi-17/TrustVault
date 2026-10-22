import React from 'react';

export const Badge = ({ variant = 'neutral', children, className = '' }) => {
  return (
    <span className={`badge badge-${variant} ${className}`}>
      {children}
    </span>
  );
};
