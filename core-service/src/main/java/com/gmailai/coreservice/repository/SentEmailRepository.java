package com.gmailai.coreservice.repository;

import com.gmailai.coreservice.model.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SentEmailRepository extends JpaRepository<SentEmail, UUID> {
}