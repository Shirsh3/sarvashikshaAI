package com.sarvashikshaai.service;

import com.sarvashikshaai.model.entity.AssemblyConfig;
import com.sarvashikshaai.repository.AssemblyConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssemblyConfigService {

    private final AssemblyConfigRepository repo;

    public AssemblyConfig getOrCreate() {
        Optional<AssemblyConfig> existing = repo.findAll().stream().findFirst();
        return existing.orElseGet(() -> {
            AssemblyConfig cfg = new AssemblyConfig();
            return repo.save(cfg);
        });
    }

    @Transactional
    public void updateUrls(String anthem, String prayer, String pledge, String hindiPrayer) {
        AssemblyConfig cfg = getOrCreate();
        cfg.setAnthemUrl(nullOrTrim(anthem));
        cfg.setPrayerUrl(nullOrTrim(prayer));
        cfg.setPledgeUrl(nullOrTrim(pledge));
        cfg.setHindiPrayerUrl(nullOrTrim(hindiPrayer));
        repo.save(cfg);
    }

    public Map<String, String> getLinks() {
        AssemblyConfig cfg = repo.findAll().stream().findFirst().orElse(null);
        Map<String, String> map = new HashMap<>();
        if (cfg == null) return map;
        if (cfg.getAnthemUrl() != null && !cfg.getAnthemUrl().isBlank()) {
            map.put("national anthem", cfg.getAnthemUrl());
        }
        if (cfg.getPrayerUrl() != null && !cfg.getPrayerUrl().isBlank()) {
            map.put("morning prayer", cfg.getPrayerUrl());
        }
        if (cfg.getPledgeUrl() != null && !cfg.getPledgeUrl().isBlank()) {
            map.put("pledge", cfg.getPledgeUrl());
        }
        if (cfg.getHindiPrayerUrl() != null && !cfg.getHindiPrayerUrl().isBlank()) {
            map.put("hindi prayer", cfg.getHindiPrayerUrl());
        }
        return map;
    }

    private String nullOrTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

