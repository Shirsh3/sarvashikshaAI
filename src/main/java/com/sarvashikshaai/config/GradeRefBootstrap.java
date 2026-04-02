package com.sarvashikshaai.config;

import com.sarvashikshaai.model.entity.GradeRef;
import com.sarvashikshaai.repository.GradeRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds {@code grade_ref} with LKG, UKG, and grades 1–12 if the table is empty.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class GradeRefBootstrap implements ApplicationRunner {

    private final GradeRefRepository gradeRefRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (gradeRefRepository.count() > 0) {
            return;
        }
        List<GradeRef> rows = new ArrayList<>();
        int order = 0;
        rows.add(g("LKG", "LKG", order++));
        rows.add(g("UKG", "UKG", order++));
        for (int i = 1; i <= 12; i++) {
            String c = String.valueOf(i);
            rows.add(g(c, "Grade " + i, order++));
        }
        gradeRefRepository.saveAll(rows);
    }

    private static GradeRef g(String code, String label, int sortOrder) {
        GradeRef r = new GradeRef();
        r.setCode(code);
        r.setLabel(label);
        r.setSortOrder(sortOrder);
        return r;
    }
}
