package com.gmailai.coreservice.repository;

import com.gmailai.coreservice.model.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {
    List<Email> findByUserId(UUID userId);
    boolean existsByGmailMessageId(String gmailMessageId);
    // Used to build thread context for AI draft generation
    List<Email> findByThreadId(String threadId);
}