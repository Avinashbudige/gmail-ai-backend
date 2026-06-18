import { useState, useEffect } from 'react'
import './index.css'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import PrivacyPolicy from './pages/PrivacyPolicy'
import TermsOfService from './pages/TermsOfService'

// API base is configurable via environment variable.
// Set VITE_API_BASE in frontend/.env for local dev or at build time for production.
const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:3000';

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Simple path-based routing for legal pages
  const currentPath = window.location.pathname;

  if (currentPath === '/privacy') {
    return <PrivacyPolicy />;
  }

  if (currentPath === '/terms') {
    return <TermsOfService />;
  }

  useEffect(() => {
    // The JWT is now in an httpOnly cookie — we cannot read it from JavaScript.
    // Instead, call /api/me with credentials:include so the cookie is sent automatically.
    // A successful response means we have a valid session; 401 means logged out.
    fetch(`${API_BASE}/api/me`, { credentials: 'include' })
      .then(res => {
        if (res.ok) return res.json();
        return null;
      })
      .then(data => {
        if (data && data.id) {
          setUser({ id: data.id, email: data.email });
        }
      })
      .catch(() => {
        // Network error or not logged in — stay on login page
      })
      .finally(() => setLoading(false));
  }, []);

  const handleLogout = async () => {
    // Clear the httpOnly cookie server-side
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      credentials: 'include'
    }).catch(() => {});
    setUser(null);
  };

  if (loading) {
    // Avoid flash of login page while checking session
    return null;
  }

  if (!user) {
    return <Login apiBase={API_BASE} />;
  }

  return <Dashboard user={user} apiBase={API_BASE} onLogout={handleLogout} />;
}

export default App
