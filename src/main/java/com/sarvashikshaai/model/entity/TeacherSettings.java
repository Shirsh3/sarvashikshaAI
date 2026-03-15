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
 * Persists the teacher's selected Google Sheet and last sync time.
 * Keyed by the teacher's Google account email — one row per teacher.
 */
@Entity
@Table(name = "teacher_settings")
@Getter
@Setter
@NoArgsConstructor
public class TeacherSettings {

    @Id
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "sheet_id")
    private String sheetId;

    @Column(name = "sheet_name")
    private String sheetName;

    @Column(name = "last_sync")
    private Instant lastSync;

    public TeacherSettings(String email) {
        this.email = email;
    }
}
