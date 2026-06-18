package com.gmailai.coreservice.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sent_emails")
public class SentEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "gmail_message_id", nullable = false)
    private String gmailMessageId;

    /**
     * The user who sent this email — required to query writing history per user
     * for AI personalization context.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * The full text that was actually sent (edited content takes priority over generated).
     * Stored so the AI can learn the user's writing style from their past replies.
     */
    @Column(name = "sent_body", columnDefinition = "TEXT")
    private String sentBody;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "thread_integrity_check")
    private boolean threadIntegrityCheck = true;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDraftId() { return draftId; }
    public void setDraftId(UUID draftId) { this.draftId = draftId; }
    public String getGmailMessageId() { return gmailMessageId; }
    public void setGmailMessageId(String gmailMessageId) { this.gmailMessageId = gmailMessageId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getSentBody() { return sentBody; }
    public void setSentBody(String sentBody) { this.sentBody = sentBody; }
    public LocalDateTime getSentAt() { return sentAt; }
    public boolean isThreadIntegrityCheck() { return threadIntegrityCheck; }
    public void setThreadIntegrityCheck(boolean threadIntegrityCheck) { this.threadIntegrityCheck = threadIntegrityCheck; }
}