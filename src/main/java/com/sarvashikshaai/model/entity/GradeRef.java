package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Canonical school grades for filters and dropdowns (KG, UKG, 1–12).
 * {@link StudentEntity#getGrade()} should use the same {@code code} values.
 */
@Entity
@Table(name = "grade_ref")
@Getter
@Setter
@NoArgsConstructor
public class GradeRef {

    @Id
    @Column(name = "code", length = 16)
    private String code;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
