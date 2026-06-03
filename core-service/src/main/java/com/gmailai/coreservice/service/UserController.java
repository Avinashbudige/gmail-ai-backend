package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.User;
import com.gmailai.coreservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

import com.gmailai.coreservice.service.GmailClient;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final GmailClient gmailClient;
    
    @Value("${google.pubsub.topic:}")
    private String pubsubTopic;

    public UserController(UserService userService, GmailClient gmailClient) {
        this.userService = userService;
        this.gmailClient = gmailClient;
    }

    @PostMapping("/sync-profile")
    public ResponseEntity<User> syncProfile(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String refreshToken = request.get("refreshToken");
        User user = userService.saveOrUpdateUser(email, refreshToken);
        
        if (pubsubTopic != null && !pubsubTopic.isEmpty()) {
            gmailClient.watchInbox(refreshToken, pubsubTopic);
        }
        
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable UUID id, @RequestHeader("X-User-ID") String xUserId) {
        if (!id.toString().equals(xUserId)) {
            return ResponseEntity.status(403).build(); // Security Check
        }
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/preferences")
    public ResponseEntity<User> updatePreferences(
            @PathVariable UUID id,
            @RequestHeader("X-User-ID") String xUserId,
            @RequestBody Map<String, Object> request) {
        
        if (!id.toString().equals(xUserId)) {
            return ResponseEntity.status(403).build(); // Security Check
        }

        String toneStr = (String) request.get("preferredTone");
        User.Tone tone = toneStr != null ? User.Tone.valueOf(toneStr.toUpperCase()) : null;
        String signature = (String) request.get("signature");
        Boolean autoApprove = (Boolean) request.get("autoApprove");

        User updatedUser = userService.updatePreferences(id, tone, signature, autoApprove);
        return ResponseEntity.ok(updatedUser);
    }
}