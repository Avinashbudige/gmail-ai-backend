import { useState, useEffect, useCallback } from 'react';

export default function Dashboard({ token, user, apiBase, onLogout }) {
  const [drafts, setDrafts] = useState([]);
  const [selectedDraft, setSelectedDraft] = useState(null);
  const [editedContent, setEditedContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [toasts, setToasts] = useState([]);
  const [activeTab, setActiveTab] = useState('pending');

  // Helper to show toast notifications
  const showToast = (message, type = 'success') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4000);
  };

  // Fetch drafts from the API
  const fetchDrafts = useCallback(async () => {
    try {
      const endpoint = activeTab === 'pending' ? '/api/drafts/pending' : '/api/drafts';
      const response = await fetch(`${apiBase}${endpoint}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.status === 401) {
        onLogout();
        return;
      }

      const data = await response.json();
      setDrafts(data);
    } catch (error) {
      console.error('Failed to fetch drafts:', error);
      showToast('Failed to fetch drafts', 'error');
    } finally {
      setLoading(false);
    }
  }, [token, apiBase, activeTab, onLogout]);

  useEffect(() => {
    fetchDrafts();
    // Auto-refresh every 15 seconds
    const interval = setInterval(fetchDrafts, 15000);
    return () => clearInterval(interval);
  }, [fetchDrafts]);

  // Select a draft for preview
  const handleSelectDraft = (draft) => {
    setSelectedDraft(draft);
    setEditedContent(draft.generatedContent || '');
  };

  // Approve and send the selected draft
  const handleApprove = async () => {
    if (!selectedDraft) return;
    setActionLoading(true);

    try {
      const response = await fetch(`${apiBase}/api/drafts/${selectedDraft.id}/approve`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.ok) {
        showToast('✅ Draft approved and sent successfully!');
        setSelectedDraft(null);
        fetchDrafts();
      } else {
        const errorData = await response.json();
        showToast(errorData.message || 'Failed to approve draft', 'error');
      }
    } catch (error) {
      showToast('Network error: Could not approve draft', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // Reject the selected draft
  const handleReject = async () => {
    if (!selectedDraft) return;
    setActionLoading(true);

    try {
      const response = await fetch(`${apiBase}/api/drafts/${selectedDraft.id}/reject`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.ok) {
        showToast('Draft rejected');
        setSelectedDraft(null);
        fetchDrafts();
      } else {
        showToast('Failed to reject draft', 'error');
      }
    } catch (error) {
      showToast('Network error: Could not reject draft', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // Save edited content
  const handleSaveEdit = async () => {
    if (!selectedDraft) return;
    setActionLoading(true);

    try {
      const response = await fetch(`${apiBase}/api/drafts/${selectedDraft.id}`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ content: editedContent })
      });

      if (response.ok) {
        showToast('Draft updated');
        fetchDrafts();
      } else {
        showToast('Failed to save changes', 'error');
      }
    } catch (error) {
      showToast('Network error: Could not save draft', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // Extract sender name from email string like "Avinash <email@example.com>"
  const getSenderName = (sender) => {
    if (!sender) return '?';
    const match = sender.match(/^([^<]+)/);
    return match ? match[1].trim() : sender;
  };

  const getSenderInitial = (sender) => {
    const name = getSenderName(sender);
    return name.charAt(0).toUpperCase();
  };

  const pendingCount = drafts.filter(d => d.status === 'PENDING').length;

  return (
    <div className="app-layout">
      {/* ---- Sidebar ---- */}
      <aside className="sidebar">
        <div className="sidebar-logo">
          <div className="sidebar-logo-icon">✉</div>
          <span className="sidebar-logo-text">Draftly</span>
        </div>

        <nav className="sidebar-nav">
          <button
            className={`sidebar-link ${activeTab === 'pending' ? 'active' : ''}`}
            onClick={() => { setActiveTab('pending'); setSelectedDraft(null); }}
          >
            <span className="sidebar-link-icon">📥</span>
            <span className="sidebar-link-label">Pending Drafts</span>
            {pendingCount > 0 && (
              <span className="sidebar-link-badge">{pendingCount}</span>
            )}
          </button>

          <button
            className={`sidebar-link ${activeTab === 'all' ? 'active' : ''}`}
            onClick={() => { setActiveTab('all'); setSelectedDraft(null); }}
          >
            <span className="sidebar-link-icon">📋</span>
            <span className="sidebar-link-label">All Drafts</span>
          </button>
        </nav>

        {user && (
          <div className="sidebar-user">
            <div className="sidebar-user-avatar">
              {user.email ? user.email.charAt(0).toUpperCase() : '?'}
            </div>
            <div className="sidebar-user-info">
              <div className="sidebar-user-name">
                {user.email ? user.email.split('@')[0] : 'User'}
              </div>
              <div className="sidebar-user-email">{user.email || ''}</div>
            </div>
          </div>
        )}
      </aside>

      {/* ---- Main Content ---- */}
      <div className="main-content">
        {/* Top Bar */}
        <header className="topbar">
          <h1 className="topbar-title">
            {activeTab === 'pending' ? 'Pending Drafts' : 'All Drafts'}
          </h1>
          <div className="topbar-actions">
            <button className="btn-icon" onClick={fetchDrafts} title="Refresh">
              🔄
            </button>
            <button className="btn-icon" onClick={onLogout} title="Logout">
              🚪
            </button>
          </div>
        </header>

        {/* Split Pane */}
        <div className="split-pane">
          {/* Left: Draft List */}
          <div className="draft-list-pane">
            <div className="draft-list-header">
              <h2>{activeTab === 'pending' ? 'Awaiting Review' : 'Draft History'}</h2>
            </div>

            <div className="draft-list-scroll">
              {loading ? (
                // Skeleton loading cards
                Array.from({ length: 4 }).map((_, i) => (
                  <div className="draft-card" key={i}>
                    <div className="skeleton" style={{ width: '60%', height: 14, marginBottom: 8 }} />
                    <div className="skeleton" style={{ width: '80%', height: 16, marginBottom: 6 }} />
                    <div className="skeleton" style={{ width: '100%', height: 36 }} />
                  </div>
                ))
              ) : drafts.length === 0 ? (
                <div className="detail-empty" style={{ padding: '60px 20px' }}>
                  <div className="detail-empty-icon">📭</div>
                  <div className="detail-empty-text">
                    {activeTab === 'pending'
                      ? 'No pending drafts. Your inbox is clear!'
                      : 'No drafts found yet.'}
                  </div>
                </div>
              ) : (
                drafts.map((draft, index) => (
                  <div
                    className={`draft-card fade-in ${selectedDraft?.id === draft.id ? 'active' : ''}`}
                    key={draft.id}
                    onClick={() => handleSelectDraft(draft)}
                    style={{ animationDelay: `${index * 50}ms` }}
                  >
                    <div className="draft-card-header">
                      <span className="draft-card-sender">
                        {getSenderName(draft.sender || 'Unknown Sender')}
                      </span>
                      <span className={`badge badge-${(draft.status || 'pending').toLowerCase()}`}>
                        {draft.status || 'PENDING'}
                      </span>
                    </div>
                    <div className="draft-card-subject">
                      {draft.subject || 'No Subject'}
                    </div>
                    <div className="draft-card-preview">
                      {draft.generatedContent
                        ? draft.generatedContent.substring(0, 120) + '...'
                        : 'No content generated.'}
                    </div>
                    <div className="draft-card-footer">
                      <span className="draft-card-cta">
                        Review Draft →
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Right: Detail / Editor Pane */}
          <div className="detail-pane">
            {!selectedDraft ? (
              <div className="detail-empty">
                <div className="detail-empty-icon">📧</div>
                <div className="detail-empty-text">Select a draft to preview</div>
              </div>
            ) : (
              <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                {/* Detail Header */}
                <div className="detail-header">
                  <div className="detail-header-sender">
                    From: {selectedDraft.sender || 'Unknown'}
                  </div>
                  <div className="detail-header-subject">
                    {selectedDraft.subject || 'No Subject'}
                  </div>
                </div>

                {/* Detail Body */}
                <div className="detail-body">
                  {/* Original Email */}
                  {selectedDraft.originalBody && (
                    <div className="detail-section">
                      <div className="detail-section-label">Original Email</div>
                      <div className="detail-original-body">
                        {selectedDraft.originalBody}
                      </div>
                    </div>
                  )}

                  {/* AI Generated Draft */}
                  <div className="detail-section">
                    <div className="detail-section-label">AI Generated Reply</div>
                    <textarea
                      className="detail-draft-editor"
                      value={editedContent}
                      onChange={(e) => setEditedContent(e.target.value)}
                      id="draft-editor"
                    />
                  </div>
                </div>

                {/* Action Buttons */}
                <div className="detail-actions">
                  <button
                    className="btn btn-primary"
                    onClick={handleApprove}
                    disabled={actionLoading}
                    id="approve-btn"
                  >
                    {actionLoading ? (
                      <div className="loading-spinner" />
                    ) : (
                      <>✅ Approve &amp; Send</>
                    )}
                  </button>

                  {editedContent !== (selectedDraft.generatedContent || '') && (
                    <button
                      className="btn btn-ghost"
                      onClick={handleSaveEdit}
                      disabled={actionLoading}
                      id="save-edit-btn"
                    >
                      💾 Save Edit
                    </button>
                  )}

                  <button
                    className="btn btn-danger"
                    onClick={handleReject}
                    disabled={actionLoading}
                    id="reject-btn"
                  >
                    ✕ Reject
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Toast Notifications */}
      <div className="toast-container">
        {toasts.map(toast => (
          <div key={toast.id} className={`toast toast-${toast.type}`}>
            {toast.message}
          </div>
        ))}
      </div>
    </div>
  );
}
