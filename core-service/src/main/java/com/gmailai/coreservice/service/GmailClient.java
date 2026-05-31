package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class GmailClient {

    @Value("${gmail.mode:mock}")
    private String gmailMode;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String clientSecret;

    private int mockEmailCounter = 0;
    private final RestTemplate restTemplate = new RestTemplate();
    private final TokenCacheService tokenCacheService;

    // Constructor injection for TokenCacheService
    public GmailClient(TokenCacheService tokenCacheService) {
        this.tokenCacheService = tokenCacheService;
    }

    // Helper method to get a fresh access token using the refresh token
    // Now delegates to TokenCacheService for caching
    private String getFreshAccessToken(String refreshToken) {
        return tokenCacheService.getAccessToken(refreshToken);
    }

    public List<Email> fetchUnreadEmails(UUID userId, String refreshToken, LocalDateTime lastSyncTime) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            return generateMockEmails(userId);
        }
        
        try {
            String accessToken = getFreshAccessToken(refreshToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=is:unread category:primary -from:no-reply";
            ResponseEntity<Map> listResponse = restTemplate.exchange(listUrl, HttpMethod.GET, entity, Map.class);
            
            List<Map<String, String>> messages = (List<Map<String, String>>) listResponse.getBody().get("messages");
            if (messages == null || messages.isEmpty()) {
                return Collections.emptyList();
            }

            List<Email> fetchedEmails = new ArrayList<>();
            for (Map<String, String> msg : messages) {
                String msgId = msg.get("id");
                String msgUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + msgId + "?format=full";
                ResponseEntity<Map> msgResponse = restTemplate.exchange(msgUrl, HttpMethod.GET, entity, Map.class);
                Map<String, Object> msgBody = msgResponse.getBody();
                
                Map<String, Object> payload = (Map<String, Object>) msgBody.get("payload");
                List<Map<String, String>> headersList = (List<Map<String, String>>) payload.get("headers");
                
                String sender = "";
                String subject = "";
                for (Map<String, String> header : headersList) {
                    if ("From".equalsIgnoreCase(header.get("name"))) sender = header.get("value");
                    if ("Subject".equalsIgnoreCase(header.get("name"))) subject = header.get("value");
                }
                
                String snippet = (String) msgBody.get("snippet");

                Email email = new Email();
                email.setUserId(userId);
                email.setGmailMessageId(msgId);
                email.setThreadId((String) msgBody.get("threadId"));
                email.setSender(sender);
                email.setSubject(subject);
                email.setBody(snippet != null ? snippet : "");
                email.setReceivedAt(LocalDateTime.now());
                fetchedEmails.add(email);
            }
            return fetchedEmails;
        } catch (Exception e) {
            System.err.println("[Live Gmail] Error fetching live emails: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Email> generateMockEmails(UUID userId) {
        List<Email> mockEmails = new ArrayList<>();
        mockEmailCounter++;

        if (mockEmailCounter % 2 == 1) {
            Email email = new Email();
            email.setUserId(userId);
            email.setGmailMessageId("mock_msg_" + System.currentTimeMillis() + "_1");
            email.setThreadId("mock_thread_" + System.currentTimeMillis() + "_1");
            email.setSender("boss@workplace.com");
            email.setSubject("Project status report updates");
            email.setBody("Hi! Could you please send me the weekly project status report by end of the day? Thanks!");
            email.setReceivedAt(LocalDateTime.now());
            mockEmails.add(email);
        } else {
            Email email = new Email();
            email.setUserId(userId);
            email.setGmailMessageId("mock_msg_" + System.currentTimeMillis() + "_2");
            email.setThreadId("mock_thread_" + System.currentTimeMillis() + "_2");
            email.setSender("friend@weekendplans.com");
            email.setSubject("Dinner plans tonight?");
            email.setBody("Hey, are we still on for dinner tonight at 7 PM? Let me know!");
            email.setReceivedAt(LocalDateTime.now());
            mockEmails.add(email);
        }
        return mockEmails;
    }

    public void insertDraft(String refreshToken, String threadId, String replyBody) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            return;
        }
    }

    public String sendEmail(String refreshToken, String threadId, String to, String subject, String body) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            return "msg_live_" + UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            String accessToken = getFreshAccessToken(refreshToken);

            String rawEmailStr = "To: " + to + "\r\n" +
                                 "Subject: " + subject + "\r\n" +
                                 "In-Reply-To: " + threadId + "\r\n\r\n" +
                                 body;
            
            String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(rawEmailStr.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("raw", encodedEmail);
            requestBody.put("threadId", threadId);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            String sendUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
            ResponseEntity<Map> response = restTemplate.exchange(sendUrl, HttpMethod.POST, entity, Map.class);
            
            return (String) response.getBody().get("id");
        } catch (Exception e) {
            System.err.println("[Live Gmail] Error sending live email: " + e.getMessage());
            throw new RuntimeException("Failed to send email");
        }
    }
}