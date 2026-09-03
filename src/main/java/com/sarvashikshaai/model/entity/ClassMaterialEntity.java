package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "class_materials")
@Getter
@Setter
@NoArgsConstructor
public class ClassMaterialEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_INDEXING = "INDEXING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String grade;

    @Column(nullable = false, length = 80)
    private String subject = "GENERAL";

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "original_filename", length = 300)
    private String originalFilename;

    @Column(name = "storage_path", length = 1024)
    private String storagePath;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
