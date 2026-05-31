package com.gmailai.coreservice.controller;

import com.gmailai.coreservice.model.Draft;
import com.gmailai.coreservice.service.DraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftService draftService;
    private final org.springframework.web.client.RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Value("${GATEWAY_URL:http://localhost:3000}")
    private String gatewayUrl;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
        this.restTemplate = new org.springframework.web.client.RestTemplate();
    }

    @GetMapping
    public ResponseEntity<List<Draft>> getAllDrafts(@RequestHeader("X-User-ID") String xUserId) {
        UUID userId = UUID.fromString(xUserId);
        return ResponseEntity.ok(draftService.getAllDraftsForUser(userId));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingDrafts(@RequestHeader("X-User-ID") String xUserId) {
        UUID userId = UUID.fromString(xUserId);
        return ResponseEntity.ok(draftService.getPendingDraftsWithEmail(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Draft> editDraft(
            @PathVariable UUID id,
            @RequestHeader("X-User-ID") String xUserId,
            @RequestBody Map<String, String> request) {
        
        UUID userId = UUID.fromString(xUserId);
        String content = request.get("content");
        if (content == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(draftService.editDraft(id, userId, content));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Draft> rejectDraft(
            @PathVariable UUID id,
            @RequestHeader("X-User-ID") String xUserId) {
        
        UUID userId = UUID.fromString(xUserId);
        return ResponseEntity.ok(draftService.rejectDraft(id, userId));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, String>> approveAndSend(
            @PathVariable UUID id,
            @RequestHeader("X-User-ID") String xUserId) {
        
        UUID userId = UUID.fromString(xUserId);
        try {
            String gmailMessageId = draftService.approveAndSendDraft(id, userId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "messageId", gmailMessageId,
                "details", "Draft sent successfully."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/refine")
    public ResponseEntity<Draft> refineDraft(
            @PathVariable UUID id,
            @RequestHeader("X-User-ID") String xUserId,
            @RequestBody Map<String, String> request) {
        
        UUID userId = UUID.fromString(xUserId);
        String prompt = request.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(draftService.refineDraft(id, userId, prompt, gatewayUrl, restTemplate));
    }
}