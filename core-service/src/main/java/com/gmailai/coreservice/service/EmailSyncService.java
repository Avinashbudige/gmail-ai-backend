package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.Draft;
import com.gmailai.coreservice.model.Email;
import com.gmailai.coreservice.model.User;
import com.gmailai.coreservice.repository.DraftRepository;
import com.gmailai.coreservice.repository.EmailRepository;
import com.gmailai.coreservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class EmailSyncService {

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final DraftRepository draftRepository;
    private final GmailClient gmailClient;
    private final UserService userService;
    private final RateLimiterService rateLimiterService;
    private final RestTemplate restTemplate = new RestTemplate();
    private ExecutorService executorService;

    @Value("${gateway.url:http://localhost:3000}")
    private String gatewayUrl;

    @Value("${email.processing.thread-pool-size:10}")
    private int threadPoolSize;

    public EmailSyncService(UserRepository userRepository, 
                            EmailRepository emailRepository,
                            DraftRepository draftRepository,
                            GmailClient gmailClient,
                            UserService userService,
                            RateLimiterService rateLimiterService) {
        this.userRepository = userRepository;
        this.emailRepository = emailRepository;
        this.draftRepository = draftRepository;
        this.gmailClient = gmailClient;
        this.userService = userService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        System.out.println("[EmailSyncService] Initialized with thread pool size: " + threadPoolSize);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[EmailSyncService] Thread pool shut down");
    }

    // Runs every 20 seconds to pull email updates in local dev mode
    @Scheduled(fixedDelay = 20000)
    public void syncEmailsForAllUsers() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                syncUserEmails(user);
            } catch (Exception e) {
                System.err.println("[Sync] Failed to sync for user " + user.getEmail() + ": " + e.getMessage());
            }
        }
    }

    public void syncUserEmails(User user) {
        String decryptedToken = userService.decryptUserToken(user);
        
        // Fetch new emails
        List<Email> fetchedEmails = gmailClient.fetchUnreadEmails(
            user.getId(), 
            decryptedToken, 
            user.getLastSyncTime()
        );

        // Create list to hold CompletableFutures for parallel processing
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Email email : fetchedEmails) {
            // Check if this message was already fetched (duplicate detection)
            if (emailRepository.existsByGmailMessageId(email.getGmailMessageId())) {
                continue;
            }

            // Process each email asynchronously in parallel
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Acquire rate limiter token (blocks until available)
                    rateLimiterService.acquire();

                    // Set email status to PROCESSING
                    email.setStatus(Email.EmailStatus.PROCESSING);
                    Email savedEmail = emailRepository.save(email);

                    // Trigger AI draft generation
                    generateAiDraftForEmail(user, savedEmail);
                    
                    return null;
                } catch (Exception e) {
                    // Error handling: isolate failures to individual emails
                    System.err.println("[EmailSync] Error processing email " + email.getGmailMessageId() + ": " + e.getMessage());
                    email.setStatus(Email.EmailStatus.FAILED);
                    emailRepository.save(email);
                    return null;
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for all emails to be processed
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Update user's last sync time after all emails are processed
        user.setLastSyncTime(LocalDateTime.now());
        userRepository.save(user);
    }

    private void generateAiDraftForEmail(User user, Email email) {
        try {
            // Post payload to Gateway AI endpoint
            String targetUrl = gatewayUrl + "/internal/ai/generate";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON); // or MediaType.APPLICATION_JSON
            
            // To ensure compatibility, we set JSON content type
            headers.set("Content-Type", "application/json");

            Map<String, Object> body = Map.of(
                "sender", email.getSender(),
                "subject", email.getSubject(),
                "body", email.getBody(),
                "tone", user.getPreferredTone().toString(),
                "signature", user.getSignature() != null ? user.getSignature() : ""
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            // Call gateway AI generator
            Map<String, String> response = restTemplate.postForObject(targetUrl, entity, Map.class);

            if (response != null && response.containsKey("draft")) {
                String aiDraftContent = response.get("draft");

                // Save draft
                Draft draft = new Draft();
                draft.setUserId(user.getId());
                draft.setEmailId(email.getId());
                draft.setGeneratedContent(aiDraftContent);
                draft.setTone(user.getPreferredTone());
                draft.setStatus(Draft.DraftStatus.PENDING);
                
                draftRepository.save(draft);

                // Update email status
                email.setStatus(Email.EmailStatus.PROCESSED);
                emailRepository.save(email);

                System.out.println("[AI-Draft] Successfully generated and stored draft for: " + email.getSender());
            } else {
                throw new RuntimeException("Empty response from AI gateway");
            }
        } catch (Exception e) {
            email.setStatus(Email.EmailStatus.FAILED);
            emailRepository.save(email);
            System.err.println("[AI-Draft] Failed to generate draft for email ID: " + email.getId() + " - " + e.getMessage());
        }
    }
}