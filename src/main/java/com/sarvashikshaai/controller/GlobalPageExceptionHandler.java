package com.sarvashikshaai.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Catch-all handler for server-side template pages so we show a friendly error page
 * instead of leaking exceptions or redirecting to login.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalPageExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handle(Exception ex, HttpServletRequest request, Model model) {
        // Default to 500. We intentionally keep the message generic.
        model.addAttribute("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("error", "Internal Server Error");
        return "error";
    }
}

