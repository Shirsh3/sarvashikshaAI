package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "cleanup_audit")
@Getter
@Setter
@NoArgsConstructor
public class CleanupAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_username", length = 120, nullable = false)
    private String actorUsername;

    @Column(name = "action_key", length = 80, nullable = false)
    private String actionKey;

    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson;

    @Column(name = "deleted_count", nullable = false)
    private long deletedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

