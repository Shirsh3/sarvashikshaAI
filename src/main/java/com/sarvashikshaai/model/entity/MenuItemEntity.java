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

/**
 * Role-based menu configuration.
 * <p>
 * SUPER_ADMIN UI edits {@code enabledTeacher}, {@code enabledAdmin}, and {@code sortOrder}.
 */
@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_key", length = 80, nullable = false, unique = true)
    private String key;

    @Column(name = "label", length = 120, nullable = false)
    private String label;

    @Column(name = "href", length = 250, nullable = false)
    private String href;

    @Column(name = "icon", length = 60)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "enabled_teacher", nullable = false)
    private boolean enabledTeacher;

    @Column(name = "enabled_admin", nullable = false)
    private boolean enabledAdmin;

    public MenuItemEntity(
            String key,
            String label,
            String href,
            String icon,
            int sortOrder,
            boolean enabledTeacher,
            boolean enabledAdmin
    ) {
        this.key = key;
        this.label = label;
        this.href = href;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.enabledTeacher = enabledTeacher;
        this.enabledAdmin = enabledAdmin;
    }
}

