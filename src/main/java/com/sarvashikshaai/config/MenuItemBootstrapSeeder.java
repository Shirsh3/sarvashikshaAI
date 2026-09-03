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
        if (repo.count() == 0) {
            seedAll();
        }
        // Ensure newer items exist on existing DBs
        ensureItem("materials", "Class PDFs", "/teacher/materials", "📄", 45, true, true);
    }

    private void seedAll() {
        List<MenuItemEntity> items = new ArrayList<>();

        int t = 0;
        items.add(item("admin_overview", "Overview", "/admin", "📊", t++, false, true));
        items.add(item("admin_analytics", "Student analytics", "/admin/analytics", "📈", t++, false, true));
        items.add(item("teacher_dashboard", "Dashboard", "/teacher/dashboard", "📊", t++, true, true));
        items.add(item("learning", "Learning", "/teacher/learning", "📚", t++, true, true));
        items.add(item("materials", "Class PDFs", "/teacher/materials", "📄", t++, true, true));
        items.add(item("reading", "Reading", "/reading", "📖", t++, true, true));
        items.add(item("quiz", "Quiz", "/quiz/teacher", "❓", t++, true, true));
        items.add(item("attendance", "Attendance", "/attendance", "✅", t++, true, true));
        items.add(item("assembly", "Assembly", "/assembly", "🌅", t++, true, true));
        items.add(item("leaderboard", "Leaderboard", "/leaderboard", "🏆", t++, true, true));
        items.add(item("students", "Students", "/teacher/setup", "⚙️", t++, true, true));

        repo.saveAll(items);
    }

    private void ensureItem(
            String key,
            String label,
            String href,
            String icon,
            int sortOrder,
            boolean enabledTeacher,
            boolean enabledAdmin
    ) {
        if (repo.findByKey(key).isPresent()) return;
        repo.save(item(key, label, href, icon, sortOrder, enabledTeacher, enabledAdmin));
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
