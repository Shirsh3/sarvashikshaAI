package com.sarvashikshaai.service;

import com.sarvashikshaai.model.entity.AssemblyConfig;
import com.sarvashikshaai.repository.AssemblyConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssemblyConfigService {

    /** Keys used for assembly playback order (see AssemblyController). */
    public static final List<String> SLOT_ORDER_KEYS = List.of("anthem", "pledge", "prayer", "hindi");

    private final AssemblyConfigRepository repo;

    public AssemblyConfig getOrCreate() {
        Optional<AssemblyConfig> existing = repo.findAll().stream().findFirst();
        return existing.orElseGet(() -> {
            AssemblyConfig cfg = new AssemblyConfig();
            return repo.save(cfg);
        });
    }

    @Transactional
    public void updateUrls(String anthem, String prayer, String pledge, String hindiPrayer, String slotOrderCsv) {
        AssemblyConfig cfg = getOrCreate();
        cfg.setAnthemUrl(nullOrTrim(anthem));
        cfg.setPrayerUrl(nullOrTrim(prayer));
        cfg.setPledgeUrl(nullOrTrim(pledge));
        cfg.setHindiPrayerUrl(nullOrTrim(hindiPrayer));
        cfg.setSlotOrder(normalizeSlotOrderCsv(slotOrderCsv));
        repo.save(cfg);
    }

    /** Legacy callers: default slot order. */
    @Transactional
    public void updateUrls(String anthem, String prayer, String pledge, String hindiPrayer) {
        updateUrls(anthem, prayer, pledge, hindiPrayer, null);
    }

    public String getResolvedSlotOrderCsv(AssemblyConfig cfg) {
        if (cfg == null) {
            return String.join(",", resolveSlotOrder(null));
        }
        return String.join(",", resolveSlotOrder(cfg.getSlotOrder()));
    }

    public List<String> resolveSlotOrder(String stored) {
        List<String> out = new ArrayList<>();
        if (stored != null && !stored.isBlank()) {
            for (String p : stored.split(",")) {
                String k = p.trim().toLowerCase(Locale.ROOT);
                if (SLOT_ORDER_KEYS.contains(k) && !out.contains(k)) {
                    out.add(k);
                }
            }
        }
        for (String k : SLOT_ORDER_KEYS) {
            if (!out.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    public String normalizeSlotOrderCsv(String csv) {
        return String.join(",", resolveSlotOrder(csv));
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

