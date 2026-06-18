package com.gmailai.coreservice.repository;

import com.gmailai.coreservice.model.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SentEmailRepository extends JpaRepository<SentEmail, UUID> {
    // Fetches the 5 most recent sent emails for this user to build AI writing style context
    List<SentEmail> findTop5ByUserIdOrderByIdDesc(UUID userId);
}