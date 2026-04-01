package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.dto.MenuItemConfigDto;
import com.sarvashikshaai.model.dto.MenuItemUpdateDto;
import com.sarvashikshaai.model.entity.MenuItemEntity;
import com.sarvashikshaai.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperAdminMenuApiController {

    private final MenuItemRepository menuItemRepository;

    @GetMapping("/menu-items")
    public List<MenuItemConfigDto> menuItems() {
        return menuItemRepository.findAll().stream()
                .sorted(Comparator.comparingInt(MenuItemEntity::getSortOrder))
                .map(this::toConfigDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/menu-items")
    public Map<String, Object> saveMenuItems(@RequestBody List<MenuItemUpdateDto> updates) {
        if (updates == null || updates.isEmpty()) {
            return Map.of("ok", true);
        }

        // Load once for efficiency.
        var existingByKey = menuItemRepository.findAll().stream()
                .collect(Collectors.toMap(MenuItemEntity::getKey, e -> e));

        for (MenuItemUpdateDto u : updates) {
            if (u == null || u.key() == null) continue;
            var e = existingByKey.get(u.key());
            if (e == null) continue;
            if (u.label() != null && !u.label().isBlank()) {
                e.setLabel(u.label().trim());
            }
            if (u.href() != null && !u.href().isBlank()) {
                e.setHref(u.href().trim());
            }
            if (u.icon() != null) {
                e.setIcon(u.icon().trim());
            }
            e.setEnabledTeacher(u.enabledTeacher());
            e.setEnabledAdmin(u.enabledAdmin());
            e.setSortOrder(u.sortOrder());
        }

        menuItemRepository.saveAll(existingByKey.values());
        return Map.of("ok", true);
    }

    private MenuItemConfigDto toConfigDto(MenuItemEntity e) {
        return new MenuItemConfigDto(
                e.getKey(),
                e.getLabel(),
                e.getHref(),
                e.getIcon(),
                e.getSortOrder(),
                e.isEnabledTeacher(),
                e.isEnabledAdmin()
        );
    }
}

