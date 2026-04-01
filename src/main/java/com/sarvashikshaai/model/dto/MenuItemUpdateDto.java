package com.sarvashikshaai.model.dto;

public record MenuItemUpdateDto(
        String key,
        String label,
        String href,
        String icon,
        boolean enabledTeacher,
        boolean enabledAdmin,
        int sortOrder
) {}

