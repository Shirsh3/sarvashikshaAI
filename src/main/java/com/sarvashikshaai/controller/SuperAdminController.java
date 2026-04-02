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

    @GetMapping("/password-reset")
    public String passwordReset() {
        return "superadmin/password-reset";
    }

    /** Old menu URL — data cleanup was removed in favour of password reset only. */
    @GetMapping("/cleanup")
    public String cleanupRedirect() {
        return "redirect:/superadmin/password-reset";
    }
}

