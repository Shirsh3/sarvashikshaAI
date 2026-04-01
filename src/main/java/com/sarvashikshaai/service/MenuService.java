package com.sarvashikshaai.service;

import com.sarvashikshaai.model.UserRole;
import com.sarvashikshaai.model.dto.MenuItemDto;
import com.sarvashikshaai.model.entity.MenuItemEntity;
import com.sarvashikshaai.repository.MenuItemRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private final MenuItemRepository menuItemRepository;

    public MenuService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    public List<MenuItemDto> menuFor(Authentication authentication) {
        UserRole role = roleFromAuthentication(authentication);
        if (role == null) {
            return List.of();
        }

        List<MenuItemEntity> rows;
        if (role == UserRole.ADMIN) {
            rows = menuItemRepository.findAllByEnabledAdminTrueOrderBySortOrderAsc();
        } else if (role == UserRole.SUPER_ADMIN) {
            rows = menuItemRepository.findAllByEnabledTeacherTrueOrEnabledAdminTrueOrderBySortOrderAsc();
        } else {
            rows = menuItemRepository.findAllByEnabledTeacherTrueOrderBySortOrderAsc();
        }

        List<MenuItemDto> base = rows.stream()
                // Keep Quiz + Reading visible for SuperAdmin (they control these)
                .filter(e -> role == UserRole.SUPER_ADMIN
                        || (!"reading".equalsIgnoreCase(e.getKey()) && !"quiz".equalsIgnoreCase(e.getKey())))
                .map(this::toDto)
                .collect(Collectors.toList());

        if (role == UserRole.SUPER_ADMIN) {
            // Super admin config page is always visible.
            base.add(new MenuItemDto(
                    "superadmin",
                    "Super Admin",
                    "/superadmin",
                    "🛡️"
            ));
            base.add(new MenuItemDto(
                    "superadmin_cleanup",
                    "Data Cleanup",
                    "/superadmin/cleanup",
                    "🧹"
            ));
        }

        return base;
    }

    private static UserRole roleFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getAuthorities() == null) {
            return null;
        }
        for (GrantedAuthority a : authentication.getAuthorities()) {
            String authority = a.getAuthority();
            if (authority == null) continue;
            if (authority.equals("ROLE_ADMIN")) return UserRole.ADMIN;
            if (authority.equals("ROLE_TEACHER")) return UserRole.TEACHER;
            if (authority.equals("ROLE_SUPER_ADMIN")) return UserRole.SUPER_ADMIN;
        }
        return null;
    }

    private MenuItemDto toDto(MenuItemEntity e) {
        String label = e.getLabel();
        if ("Teacher Dashboard".equalsIgnoreCase(label)) {
            label = "Dashboard";
        }
        return new MenuItemDto(
                e.getKey(),
                label,
                e.getHref(),
                e.getIcon()
        );
    }
}

