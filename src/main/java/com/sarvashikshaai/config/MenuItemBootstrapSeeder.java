package com.sarvashikshaai.config;

import com.sarvashikshaai.model.entity.MenuItemEntity;
import com.sarvashikshaai.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds menu items in DB (only when table is empty).
 * <p>
 * Tied to {@code auth.bootstrap.enabled=true} because current app has no signup flow.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.bootstrap", name = "enabled", havingValue = "true")
public class MenuItemBootstrapSeeder implements ApplicationRunner {

    private final MenuItemRepository repo;

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() > 0) return;

        List<MenuItemEntity> items = new ArrayList<>();

        // Sort order roughly matches the previous hardcoded lists.
        // Note: disabled items for a role are filtered out before ordering,
        // so we can use a single sortOrder space to satisfy both roles.
        int t = 0;

        // Admin-only (appear first in admin sidebar)
        items.add(item("admin_overview", "Overview", "/admin", "📊", t++, false, true));
        items.add(item("admin_analytics", "Student analytics", "/admin/analytics", "📈", t++, false, true));

        // Teacher + Admin shared
        items.add(item("teacher_dashboard", "Dashboard", "/teacher/dashboard", "📊", t++, true, true));
        items.add(item("learning", "Learning", "/teacher/learning", "📚", t++, true, true));
        items.add(item("reading", "Reading", "/reading", "📖", t++, true, true));
        items.add(item("quiz", "Quiz", "/quiz/teacher", "❓", t++, true, true));
        items.add(item("attendance", "Attendance", "/attendance", "✅", t++, true, true));
        items.add(item("assembly", "Assembly", "/assembly", "🌅", t++, true, true));
        items.add(item("leaderboard", "Leaderboard", "/leaderboard", "🏆", t++, true, true));
        items.add(item("students", "Students", "/teacher/setup", "⚙️", t++, true, true));

        repo.saveAll(items);
    }

    private static MenuItemEntity item(
            String key,
            String label,
            String href,
            String icon,
            int sortOrder,
            boolean enabledTeacher,
            boolean enabledAdmin
    ) {
        return new MenuItemEntity(key, label, href, icon, sortOrder, enabledTeacher, enabledAdmin);
    }
}

