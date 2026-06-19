package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    public GmailClient(TokenCacheService tokenCacheService) {
        this.tokenCacheService = tokenCacheService;
    }

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
                // format=full gives us the complete payload including all headers and body parts
                String msgUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + msgId + "?format=full";
                ResponseEntity<Map> msgResponse = restTemplate.exchange(msgUrl, HttpMethod.GET, entity, Map.class);
                Map<String, Object> msgBody = msgResponse.getBody();

                Map<String, Object> payload = (Map<String, Object>) msgBody.get("payload");
                List<Map<String, String>> headersList = (List<Map<String, String>>) payload.get("headers");

                String sender = "";
                String subject = "";
                String messageIdHeader = "";
                String referencesHeader = "";

                for (Map<String, String> header : headersList) {
                    String name = header.get("name");
                    String value = header.get("value");
                    if ("From".equalsIgnoreCase(name)) sender = value;
                    if ("Subject".equalsIgnoreCase(name)) subject = value;
                    // RFC threading headers — these are NOT the same as Gmail's internal threadId
                    if ("Message-ID".equalsIgnoreCase(name)) messageIdHeader = value;
                    if ("References".equalsIgnoreCase(name)) referencesHeader = value;
                }

                // Extract full plain-text body from the MIME parts tree
                String fullBody = extractPlainTextBody(payload);
                // Fall back to snippet (250 chars) if no plain-text part found
                if (fullBody == null || fullBody.isBlank()) {
                    fullBody = (String) msgBody.get("snippet");
                }

                // Use the actual email timestamp from Gmail (internalDate = millis since epoch)
                // NOT LocalDateTime.now() which was incorrect
                Long internalDateMs = Long.parseLong((String) msgBody.get("internalDate"));
                LocalDateTime receivedAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(internalDateMs), ZoneId.systemDefault()
                );

                Email email = new Email();
                email.setUserId(userId);
                email.setGmailMessageId(msgId);
                email.setThreadId((String) msgBody.get("threadId"));
                email.setSender(sender);
                email.setSubject(subject);
                email.setBody(fullBody != null ? fullBody : "");
                email.setReceivedAt(receivedAt);
                email.setMessageIdHeader(messageIdHeader);
                email.setReferencesHeader(referencesHeader);
                fetchedEmails.add(email);
            }
            return fetchedEmails;
        } catch (Exception e) {
            System.err.println("[Live Gmail] Error fetching live emails: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Recursively walks the Gmail MIME payload parts tree to extract the plain-text body.
     * Gmail messages can be multipart/alternative (text + html) or multipart/mixed (attachments).
     * We always prefer text/plain for the AI, which avoids feeding raw HTML markup into the prompt.
     */
    private String extractPlainTextBody(Map<String, Object> payload) {
        if (payload == null) return null;

        String mimeType = (String) payload.get("mimeType");

        // Leaf node: text/plain part — decode it
        if ("text/plain".equalsIgnoreCase(mimeType)) {
            Map<String, Object> body = (Map<String, Object>) payload.get("body");
            if (body != null) {
                String data = (String) body.get("data");
                if (data != null) {
                    byte[] decoded = Base64.getUrlDecoder().decode(data);
                    return new String(decoded, StandardCharsets.UTF_8);
                }
            }
            return null;
        }

        // Container node: recurse into parts
        List<Map<String, Object>> parts = (List<Map<String, Object>>) payload.get("parts");
        if (parts != null) {
            for (Map<String, Object> part : parts) {
                String result = extractPlainTextBody(part);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        }

        return null;
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
            email.setMessageIdHeader("<mock-msg-1-" + System.currentTimeMillis() + "@workplace.com>");
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
            email.setMessageIdHeader("<mock-msg-2-" + System.currentTimeMillis() + "@weekendplans.com>");
            mockEmails.add(email);
        }
        return mockEmails;
    }

    public void insertDraft(String refreshToken, String threadId, String replyBody) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            return;
        }
    }

    /**
     * Sends an email reply via Gmail API with correct RFC 2822 threading headers.
     *
     * @param refreshToken  user's decrypted OAuth refresh token
     * @param email         the original Email entity (provides RFC headers for threading)
     * @param to            recipient address
     * @param subject       reply subject
     * @param body          reply body content
     * @return Gmail message ID of the sent message
     */
    public String sendEmail(String refreshToken, Email email, String to, String subject, String body) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            return "msg_live_" + UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            String accessToken = getFreshAccessToken(refreshToken);

            // Build RFC 2822 raw email with correct threading headers.
            // In-Reply-To and References use the original email's Message-ID header value,
            // NOT Gmail's internal threadId which is a different concept entirely.
            String messageIdHeader = email.getMessageIdHeader() != null ? email.getMessageIdHeader() : "";
            String existingRefs   = email.getReferencesHeader() != null ? email.getReferencesHeader() : "";
            // Build the References chain: existing references + the message we're replying to
            String referencesChain = existingRefs.isBlank()
                ? messageIdHeader
                : existingRefs + " " + messageIdHeader;

            String rawEmailStr =
                "To: " + to + "\r\n" +
                "Subject: " + subject + "\r\n" +
                "In-Reply-To: " + messageIdHeader + "\r\n" +
                "References: " + referencesChain + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                body;

            String encodedEmail = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawEmailStr.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("raw", encodedEmail);
            // Gmail threadId still used here to group the message in Gmail's UI thread
            requestBody.put("threadId", email.getThreadId());

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            String sendUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
            ResponseEntity<Map> response = restTemplate.exchange(sendUrl, HttpMethod.POST, entity, Map.class);

            return (String) response.getBody().get("id");
        } catch (Exception e) {
            System.err.println("[Live Gmail] Error sending live email: " + e.getMessage());
            throw new RuntimeException("Failed to send email");
        }
    }

    public void watchInbox(String refreshToken, String topicName) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            return;
        }
        try {
            String accessToken = getFreshAccessToken(refreshToken);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("topicName", topicName);
            requestBody.put("labelIds", Collections.singletonList("INBOX"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String watchUrl = "https://gmail.googleapis.com/gmail/v1/users/me/watch";

            restTemplate.exchange(watchUrl, HttpMethod.POST, entity, Map.class);
            System.out.println("[Pub/Sub] Successfully subscribed user inbox to topic: " + topicName);
        } catch (Exception e) {
            System.err.println("[Pub/Sub] Failed to subscribe user to topic: " + e.getMessage());
        }
    }
}