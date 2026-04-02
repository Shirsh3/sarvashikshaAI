package com.sarvashikshaai.controller;

import com.sarvashikshaai.service.AssemblyConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperAdminAssemblyApiController {

    private final AssemblyConfigService assemblyConfigService;

    @GetMapping("/assembly-slot-order")
    public Map<String, String> getAssemblySlotOrder() {
        var cfg = assemblyConfigService.getOrCreate();
        return Map.of("csv", assemblyConfigService.getResolvedSlotOrderCsv(cfg));
    }

    @PostMapping("/assembly-slot-order")
    public Map<String, Object> saveAssemblySlotOrder(@RequestBody(required = false) Map<String, String> body) {
        String csv = body != null ? body.get("csv") : null;
        assemblyConfigService.updateSlotOrderCsv(csv);
        var cfg = assemblyConfigService.getOrCreate();
        return Map.of("ok", true, "csv", assemblyConfigService.getResolvedSlotOrderCsv(cfg));
    }
}
