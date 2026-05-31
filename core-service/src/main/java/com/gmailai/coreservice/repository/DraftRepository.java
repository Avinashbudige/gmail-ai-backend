package com.gmailai.coreservice.repository;

import com.gmailai.coreservice.model.Draft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DraftRepository extends JpaRepository<Draft, UUID> {
    List<Draft> findByUserIdAndStatus(UUID userId, Draft.DraftStatus status);
    List<Draft> findByUserId(UUID userId);
    Optional<Draft> findByEmailId(UUID emailId);
}