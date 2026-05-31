package com.gmailai.coreservice.service;
import com.gmailai.coreservice.repository.EmailRepository;   
import com.gmailai.coreservice.model.Draft;
import com.gmailai.coreservice.repository.DraftRepository;
import org.springframework.stereotype.Service;
import java.util.Map; 
import java.util.HashMap; 
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DraftService {

    private final DraftRepository draftRepository;
    private final SendService sendService;
    private final EmailRepository emailRepository; 
    public DraftService(DraftRepository draftRepository, SendService sendService,EmailRepository emailRepository) {
        this.draftRepository = draftRepository;
        this.sendService = sendService;
        this.emailRepository = emailRepository;
    }

    public List<Map<String, Object>> getPendingDraftsWithEmail(UUID userId) {
        List<Draft> drafts = draftRepository.findByUserIdAndStatus(userId, Draft.DraftStatus.PENDING);
        
        return drafts.stream().map(draft -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", draft.getId());
            map.put("generatedContent", draft.getGeneratedContent());
            map.put("status", draft.getStatus());
            
            // Attach original email details for the frontend UI!
            emailRepository.findById(draft.getEmailId()).ifPresent(email -> {
                map.put("sender", email.getSender());
                map.put("subject", email.getSubject());
                map.put("originalBody", email.getBody());
            });
            return map;
        }).toList();
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