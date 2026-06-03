package com.gmailai.coreservice.controller;

import com.gmailai.coreservice.model.User;
import com.gmailai.coreservice.repository.UserRepository;
import com.gmailai.coreservice.service.EmailSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final UserRepository userRepository;
    private final EmailSyncService emailSyncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(UserRepository userRepository, EmailSyncService emailSyncService) {
        this.userRepository = userRepository;
        this.emailSyncService = emailSyncService;
    }

    @PostMapping("/gmail")
    public ResponseEntity<String> handleGmailWebhook(@RequestBody Map<String, Object> payload) {
        try {
            // Extract the message data from the Pub/Sub payload
            Map<String, Object> message = (Map<String, Object>) payload.get("message");
            if (message == null || !message.containsKey("data")) {
                return ResponseEntity.badRequest().body("Invalid payload");
            }

            // Decode the base64 data
            String base64Data = (String) message.get("data");
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            String decodedString = new String(decodedBytes);

            // Parse the decoded JSON to get the emailAddress
            Map<String, Object> dataMap = objectMapper.readValue(decodedString, new TypeReference<Map<String, Object>>() {});
            String emailAddress = (String) dataMap.get("emailAddress");

            if (emailAddress != null) {
                System.out.println("[Webhook] Received notification for: " + emailAddress);
                
                // Find user and trigger sync asynchronously to avoid Google Pub/Sub timeout (10s)
                Optional<User> userOpt = userRepository.findByEmail(emailAddress);
                if (userOpt.isPresent()) {
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        emailSyncService.syncUserEmails(userOpt.get());
                    });
                } else {
                    System.out.println("[Webhook] User not found for email: " + emailAddress);
                }
            }

            // Always return 200 OK to Google Pub/Sub to acknowledge receipt
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            System.err.println("[Webhook] Error processing payload: " + e.getMessage());
            // Still return 200 OK to prevent Google from retrying a bad payload endlessly
            return ResponseEntity.ok("Error processed");
        }
    }
}
