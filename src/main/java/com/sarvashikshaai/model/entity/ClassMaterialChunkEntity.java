package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "class_material_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_class_material_chunks_material_idx",
                columnNames = {"material_id", "chunk_index"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ClassMaterialChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** JSON array of floats from the embedding model. */
    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "token_estimate")
    private Integer tokenEstimate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
