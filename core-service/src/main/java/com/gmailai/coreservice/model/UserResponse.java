package com.gmailai.coreservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserResponse DTO — the public-facing representation of a User.
 *
 * Deliberately omits encryptedRefreshToken. The User JPA entity is
 * an internal DB mapping and must NEVER be serialized directly into
 * API responses, because it exposes the encrypted OAuth token.
 *
 * All UserController endpoints return this DTO instead of the User entity.
 */
public class UserResponse {

    private UUID id;
    private String email;
    private String signature;
    private User.Tone preferredTone;
    private boolean autoApprove;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Factory method — converts User entity to safe response DTO
    public static UserResponse from(User user) {
        UserResponse dto = new UserResponse();
        dto.id = user.getId();
        dto.email = user.getEmail();
        dto.signature = user.getSignature();
        dto.preferredTone = user.getPreferredTone();
        dto.autoApprove = user.isAutoApprove();
        dto.lastSyncTime = user.getLastSyncTime();
        dto.createdAt = user.getCreatedAt();
        dto.updatedAt = user.getUpdatedAt();
        return dto;
    }

    // Getters
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getSignature() { return signature; }
    public User.Tone getPreferredTone() { return preferredTone; }
    public boolean isAutoApprove() { return autoApprove; }
    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
