package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.dto.MenuItemDto;
import com.sarvashikshaai.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/menu")
    public List<MenuItemDto> menu(Authentication authentication) {
        return menuService.menuFor(authentication);
    }
}

