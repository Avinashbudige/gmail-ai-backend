package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.Draft;
import com.gmailai.coreservice.repository.DraftRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DraftService {

    private final DraftRepository draftRepository;
    private final SendService sendService;

    public DraftService(DraftRepository draftRepository, SendService sendService) {
        this.draftRepository = draftRepository;
        this.sendService = sendService;
    }

    public List<Draft> getPendingDraftsForUser(UUID userId) {
        return draftRepository.findByUserIdAndStatus(userId, Draft.DraftStatus.PENDING);
    }

    public List<Draft> getAllDraftsForUser(UUID userId) {
        return draftRepository.findByUserId(userId);
    }

    public Draft editDraft(UUID draftId, UUID userId, String newContent) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (!draft.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized action.");
        }

        draft.setEditedContent(newContent);
        draft.setStatus(Draft.DraftStatus.EDITED);
        draft.setEditedAt(LocalDateTime.now());
        return draftRepository.save(draft);
    }

    public Draft rejectDraft(UUID draftId, UUID userId) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (!draft.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized action.");
        }

        draft.setStatus(Draft.DraftStatus.REJECTED);
        draft.setRejectedAt(LocalDateTime.now());
        return draftRepository.save(draft);
    }

    public String approveAndSendDraft(UUID draftId, UUID userId) {
        return sendService.sendApprovedDraft(draftId, userId);
    }
}