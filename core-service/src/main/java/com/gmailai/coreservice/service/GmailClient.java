package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class GmailClient {

    @Value("${gmail.mode:mock}")
    private String gmailMode;

    // A counter to simulate new incoming messages in Mock Mode
    private int mockEmailCounter = 0;

    public List<Email> fetchUnreadEmails(UUID userId, String accessToken, LocalDateTime lastSyncTime) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            return generateMockEmails(userId);
        }
        
        // Live Mode Google API integration placeholder:
        // (You would call the Gmail API: https://gmail.googleapis.com/gmail/v1/users/me/messages)
        return Collections.emptyList();
    }

    private List<Email> generateMockEmails(UUID userId) {
        List<Email> mockEmails = new ArrayList<>();
        mockEmailCounter++;

        if (mockEmailCounter % 2 == 1) {
            // Mock Email 1
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
            // Mock Email 2
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

    public void insertDraft(String accessToken, String threadId, String replyBody) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            System.out.println("[Mock Gmail] Saved draft reply in Gmail draft folder for thread: " + threadId);
            return;
        }
        // Live Mode: Call Gmail API to create actual draft in user's Gmail inbox
    }

    public String sendEmail(String accessToken, String threadId, String to, String subject, String body) {
        if ("mock".equalsIgnoreCase(gmailMode)) {
            String sentMessageId = "sent_msg_" + UUID.randomUUID().toString().substring(0, 8);
            System.out.println("[Mock Gmail] Message sent to: " + to + " | Thread: " + threadId + " | Sent ID: " + sentMessageId);
            return sentMessageId;
        }
        // Live Mode: Send email using standard Gmail API
        return "msg_live_" + UUID.randomUUID().toString().substring(0, 8);
    }
}