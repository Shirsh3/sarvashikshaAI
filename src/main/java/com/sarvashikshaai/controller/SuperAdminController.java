package com.sarvashikshaai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    @GetMapping
    public String menuConfig() {
        return "superadmin/menu-config";
    }

    @GetMapping("/cleanup")
    public String cleanup() {
        return "superadmin/data-cleanup";
    }
}

