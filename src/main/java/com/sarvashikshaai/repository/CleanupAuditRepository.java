package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.CleanupAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CleanupAuditRepository extends JpaRepository<CleanupAuditRecord, Long> {
}

