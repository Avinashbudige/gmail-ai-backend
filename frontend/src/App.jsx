import { useState, useEffect } from 'react'
import './index.css'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'

import PrivacyPolicy from './pages/PrivacyPolicy'
import TermsOfService from './pages/TermsOfService'

const API_BASE = 'https://draftly.email';

function App() {
  const [token, setToken] = useState(null);
  const [user, setUser] = useState(null);

  // Simple path-based routing for legal pages
  const currentPath = window.location.pathname;

  if (currentPath === '/privacy') {
    return <PrivacyPolicy />;
  }

  if (currentPath === '/terms') {
    return <TermsOfService />;
  }

  useEffect(() => {
    // Check if we already have a token saved
    const savedToken = localStorage.getItem('draftly_token');
    const savedUser = localStorage.getItem('draftly_user');

    if (savedToken) {
      setToken(savedToken);
      if (savedUser) {
        setUser(JSON.parse(savedUser));
      }
    }

    // Check if we just came back from OAuth (token in the URL)
    const urlParams = new URLSearchParams(window.location.search);
    const urlToken = urlParams.get('token');

    if (urlToken) {
      localStorage.setItem('draftly_token', urlToken);
      setToken(urlToken);

      // Decode the JWT to get user info (payload is the 2nd part)
      try {
        const payload = JSON.parse(atob(urlToken.split('.')[1]));
        const userData = { id: payload.id, email: payload.email };
        localStorage.setItem('draftly_user', JSON.stringify(userData));
        setUser(userData);
      } catch (e) {
        console.error('Failed to decode JWT:', e);
      }

      // Clean the URL so the token isn't visible in the browser bar
      window.history.replaceState({}, document.title, '/dashboard');
    }
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('draftly_token');
    localStorage.removeItem('draftly_user');
    setToken(null);
    setUser(null);
  };

  if (!token) {
    return <Login apiBase={API_BASE} />;
  }

  return <Dashboard token={token} user={user} apiBase={API_BASE} onLogout={handleLogout} />;
}

export default App
