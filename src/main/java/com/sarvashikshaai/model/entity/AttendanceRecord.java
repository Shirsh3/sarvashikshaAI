package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One attendance mark per student per day.
 * Unique constraint ensures only one record per (studentName, date).
 */
@Entity
@Table(name = "attendance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_name", "date"}))
@Getter @Setter @NoArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "present", nullable = false)
    private boolean present;

    @Column(name = "marked_at")
    private Instant markedAt;

    public AttendanceRecord(String studentName, LocalDate date, boolean present) {
        this.studentName = studentName;
        this.date        = date;
        this.present     = present;
        this.markedAt    = Instant.now();
    }
}
