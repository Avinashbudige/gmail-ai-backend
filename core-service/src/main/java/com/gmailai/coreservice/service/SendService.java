package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.*;
import com.gmailai.coreservice.repository.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SendService {

    private final DraftRepository draftRepository;
    private final EmailRepository emailRepository;
    private final SentEmailRepository sentEmailRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final GmailClient gmailClient;
    private final UserService userService;

    public SendService(DraftRepository draftRepository,
                       EmailRepository emailRepository,
                       SentEmailRepository sentEmailRepository,
                       IdempotencyKeyRepository idempotencyKeyRepository,
                       GmailClient gmailClient,
                       UserService userService) {
        this.draftRepository = draftRepository;
        this.emailRepository = emailRepository;
        this.sentEmailRepository = sentEmailRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.gmailClient = gmailClient;
        this.userService = userService;
    }

    @Transactional
    @Retryable(
        retryFor = { RuntimeException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public String sendApprovedDraft(UUID draftId, UUID userId) {
        // 1. Idempotency Check: prevent duplicate sends
        String idempotencyKey = "send:" + userId + ":" + draftId;
        if (idempotencyKeyRepository.existsById(idempotencyKey)) {
            IdempotencyKey keyRecord = idempotencyKeyRepository.findById(idempotencyKey).orElseThrow();
            System.out.println("[SendService] Email already sent! Returning existing message ID: " + keyRecord.getGmailMessageId());
            return keyRecord.getGmailMessageId();
        }

        // 2. Fetch the draft and related email
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (!draft.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized: This draft does not belong to you.");
        }

        if (draft.getStatus() == Draft.DraftStatus.SENT) {
            throw new IllegalStateException("Draft has already been sent.");
        }

        Email email = emailRepository.findById(draft.getEmailId())
                .orElseThrow(() -> new IllegalArgumentException("Source email not found for draft."));

        // Get and decrypt user refresh token
        User user = userService.findById(userId).orElseThrow();
        String decryptedToken = userService.decryptUserToken(user);

        // Determine which content to send (edited content has priority over generated)
        String bodyToSend = draft.getEditedContent() != null ? draft.getEditedContent() : draft.getGeneratedContent();

        System.out.println("[SendService] Attempting to send email for draft " + draftId + " (Attempting connection...)");

        // 3. Send using Gmail client
        // Pass the full Email entity so GmailClient can access RFC headers (Message-ID, References)
        // for correct SMTP reply threading — Gmail's threadId is different from RFC's Message-ID
        String gmailMessageId = gmailClient.sendEmail(
                decryptedToken,
                email,                          // full Email for RFC threading headers
                email.getSender(),
                "Re: " + email.getSubject(),
                bodyToSend
        );

        // 4. Save audit log for sent email, including the sent body and userId for AI writing history
        SentEmail sentEmail = new SentEmail();
        sentEmail.setDraftId(draftId);
        sentEmail.setGmailMessageId(gmailMessageId);
        sentEmail.setUserId(userId);
        sentEmail.setSentBody(bodyToSend);  // captured for AI personalization context
        sentEmailRepository.save(sentEmail);

        // 5. Store idempotency key (expire in 24 hours to clean database)
        IdempotencyKey keyRecord = new IdempotencyKey();
        keyRecord.setId(idempotencyKey);
        keyRecord.setGmailMessageId(gmailMessageId);
        keyRecord.setExpiresAt(LocalDateTime.now().plusHours(24));
        idempotencyKeyRepository.save(keyRecord);

        // 6. Update draft status
        draft.setStatus(Draft.DraftStatus.SENT);
        draft.setApprovedAt(LocalDateTime.now());
        draftRepository.save(draft);

        return gmailMessageId;
    }
}