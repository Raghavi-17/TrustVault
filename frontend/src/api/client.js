const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

/**
 * Reads cookie value by name
 */
function getCookie(name) {
  const match = document.cookie.match(new RegExp('(^|;\\s*)(' + name + ')=([^;]*)'));
  return match ? decodeURIComponent(match[3]) : null;
}

let inMemoryCsrfToken = null;

export function setCsrfToken(token) {
  if (token) {
    inMemoryCsrfToken = token;
  }
}

/**
 * Reusable secure HTTP request helper
 */
async function request(endpoint, options = {}) {
  const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;
  
  const headers = {
    ...(options.headers || {}),
  };

  // If not FormData, default to application/json
  if (!(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  // Attach CSRF token if state-changing request
  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes((options.method || 'GET').toUpperCase())) {
    const token = getCookie('XSRF-TOKEN') || inMemoryCsrfToken;
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }

  const config = {
    ...options,
    headers,
    credentials: 'include', // essential for HTTP-only JSESSIONID cookie
  };

  const response = await fetch(url, config);

  // If blob response requested (download or preview), return blob directly
  if (options.responseType === 'blob') {
    if (!response.ok) {
      let errorMessage = 'Failed to retrieve file resource';
      try {
        const errorText = await response.text();
        const json = JSON.parse(errorText);
        errorMessage = json.message || json.error || errorText;
      } catch (e) {
        // use fallback or raw text
      }
      const error = new Error(errorMessage);
      error.status = response.status;
      throw error;
    }
    return response.blob();
  }

  // Parse JSON response
  let data;
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    data = await response.json();
  } else {
    data = await response.text();
  }

  if (!response.ok) {
    const errorMessage = data?.message || data?.error || (typeof data === 'string' ? data : 'Request failed');
    const error = new Error(errorMessage);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}

export const api = {
  get: (endpoint, options) => request(endpoint, { ...options, method: 'GET' }),
  post: (endpoint, body, options) => request(endpoint, { ...options, method: 'POST', body: body instanceof FormData ? body : JSON.stringify(body) }),
  put: (endpoint, body, options) => request(endpoint, { ...options, method: 'PUT', body: body instanceof FormData ? body : JSON.stringify(body) }),
  delete: (endpoint, options) => request(endpoint, { ...options, method: 'DELETE' }),
  
  // Dedicated helper for multipart file uploads
  upload: (endpoint, formData, options) => {
    return request(endpoint, {
      ...options,
      method: 'POST',
      body: formData,
    });
  },

  // Dedicated helper for in-browser document preview blob
  preview: async (documentId) => {
    return request(`/documents/${documentId}/preview`, { responseType: 'blob' });
  },

  // Dedicated helper for file downloads
  download: async (documentId, filename) => {
    const blob = await request(`/documents/${documentId}/download`, { responseType: 'blob' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename || 'downloaded-document';
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  }
};
