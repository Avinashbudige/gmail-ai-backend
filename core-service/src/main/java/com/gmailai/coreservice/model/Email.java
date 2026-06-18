package com.gmailai.coreservice.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "emails")
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "gmail_message_id", unique = true, nullable = false)
    private String gmailMessageId;

    @Column(name = "thread_id")
    private String threadId;

    private String sender;
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /**
     * The RFC 2822 Message-ID header value of the original email (e.g. <abc123@mail.gmail.com>).
     * Used as the In-Reply-To header when sending replies — distinct from Gmail's internal threadId.
     */
    @Column(name = "message_id_header", length = 1000)
    private String messageIdHeader;

    /**
     * The RFC 2822 References header chain from the original email.
     * Appended with the original messageIdHeader when sending to maintain full thread context
     * in non-Gmail email clients.
     */
    @Column(name = "references_header", columnDefinition = "TEXT")
    private String referencesHeader;

    @Enumerated(EnumType.STRING)
    private EmailStatus status = EmailStatus.UNPROCESSED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum EmailStatus {
        UNPROCESSED, PROCESSING, PROCESSED, FAILED
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getGmailMessageId() { return gmailMessageId; }
    public void setGmailMessageId(String gmailMessageId) { this.gmailMessageId = gmailMessageId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getMessageIdHeader() { return messageIdHeader; }
    public void setMessageIdHeader(String messageIdHeader) { this.messageIdHeader = messageIdHeader; }
    public String getReferencesHeader() { return referencesHeader; }
    public void setReferencesHeader(String referencesHeader) { this.referencesHeader = referencesHeader; }
    public EmailStatus getStatus() { return status; }
    public void setStatus(EmailStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}