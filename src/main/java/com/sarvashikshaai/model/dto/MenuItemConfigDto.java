package com.sarvashikshaai.model.dto;

public record MenuItemConfigDto(
        String key,
        String label,
        String href,
        String icon,
        int sortOrder,
        boolean enabledTeacher,
        boolean enabledAdmin
) {}

