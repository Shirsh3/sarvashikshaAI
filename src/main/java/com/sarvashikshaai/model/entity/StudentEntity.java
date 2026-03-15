package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted student profile, synced from the teacher's Google Sheet.
 * Student name is the unique key (single-NGO deployment assumption).
 * Survives server restarts — no need to re-sync from Sheet on every startup.
 */
@Entity
@Table(name = "students")
@Getter @Setter @NoArgsConstructor
public class StudentEntity {

    @Id
    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "grade")
    private String grade;

    @Column(name = "strength", length = 500)
    private String strength;

    @Column(name = "weakness", length = 500)
    private String weakness;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "active")
    private boolean active;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    public StudentEntity(String name, String grade, String strength,
                         String weakness, String notes, boolean active) {
        this.name         = name;
        this.grade        = grade;
        this.strength     = strength;
        this.weakness     = weakness;
        this.notes        = notes;
        this.active       = active;
        this.lastSyncedAt = Instant.now();
    }
}
