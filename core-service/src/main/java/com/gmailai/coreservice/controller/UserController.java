package com.gmailai.coreservice.controller;

import com.gmailai.coreservice.model.User;
import com.gmailai.coreservice.model.UserResponse;
import com.gmailai.coreservice.service.GmailClient;
import com.gmailai.coreservice.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * UserController — handles user profile sync and preference updates.
 *
 * All endpoints return UserResponse (DTO) instead of the raw User entity
 * to ensure encryptedRefreshToken is never serialized into API responses.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final GmailClient gmailClient;
    private final com.gmailai.coreservice.service.EmailSyncService emailSyncService;

    @Value("${google.pubsub.topic:}")
    private String pubsubTopic;

    public UserController(UserService userService, GmailClient gmailClient, com.gmailai.coreservice.service.EmailSyncService emailSyncService) {
        this.userService = userService;
        this.gmailClient = gmailClient;
        this.emailSyncService = emailSyncService;
    }

    /**
     * Called by the gateway after OAuth callback to create/update the user record
     * and register the Gmail Pub/Sub watch subscription.
     */
    @PostMapping("/sync-profile")
    public ResponseEntity<UserResponse> syncProfile(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String refreshToken = request.get("refreshToken");
        User user = userService.saveOrUpdateUser(email, refreshToken);

        if (pubsubTopic != null && !pubsubTopic.isEmpty()) {
            gmailClient.watchInbox(refreshToken, pubsubTopic);
        }

        // Trigger an immediate asynchronous sync so the dashboard populates instantly!
        CompletableFuture.runAsync(() -> {
            try {
                emailSyncService.syncUserEmails(user);
            } catch (Exception e) {
                System.err.println("[UserController] Initial sync failed for " + user.getEmail() + ": " + e.getMessage());
            }
        });

        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Returns the public user profile for the authenticated user.
     * Path ID must match the X-User-ID header (set by gateway from JWT).
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable UUID id,
            @RequestHeader("X-User-ID") String xUserId) {

        if (!id.toString().equals(xUserId)) {
            return ResponseEntity.status(403).build();
        }
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates the user's preferred tone, email signature, and auto-approve setting.
     * Path ID must match the X-User-ID header.
     */
    @PutMapping("/{id}/preferences")
    public ResponseEntity<UserResponse> updatePreferences(
            @PathVariable UUID id,
            @RequestHeader("X-User-ID") String xUserId,
            @RequestBody Map<String, Object> request) {

        if (!id.toString().equals(xUserId)) {
            return ResponseEntity.status(403).build();
        }

        String toneStr = (String) request.get("preferredTone");
        User.Tone tone = toneStr != null ? User.Tone.valueOf(toneStr.toUpperCase()) : null;
        String signature = (String) request.get("signature");
        Boolean autoApprove = (Boolean) request.get("autoApprove");

        User updatedUser = userService.updatePreferences(id, tone, signature, autoApprove);
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }
}
