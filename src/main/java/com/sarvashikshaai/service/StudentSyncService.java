package com.sarvashikshaai.service;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.model.entity.TeacherSettings;
import com.sarvashikshaai.repository.StudentEntityRepository;
import com.sarvashikshaai.repository.TeacherSettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the student list used across the app.
 *
 * Source of truth chain:
 *   1. On sync: Google Sheet → upsert into H2 → update in-memory cache
 *   2. On startup: H2 → in-memory cache (no Sheet call needed)
 *
 * Assembly links and thoughts still come from Google Sheet on sync
 * (they are config, not transactional data, so H2 persistence isn't needed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentSyncService {

    private final GoogleSheetsService       sheetsService;
    private final TeacherSettingsRepository settingsRepo;
    private final StudentEntityRepository   studentEntityRepo;

    // ── In-memory caches ──────────────────────────────────────────────────────

    /** teacher email → active students (populated from H2 on startup, refreshed on sync) */
    private final Map<String, List<Student>> cache = new ConcurrentHashMap<>();

    /** teacher email → assembly section → YouTube URL */
    private final Map<String, Map<String, String>> assemblyCache = new ConcurrentHashMap<>();

    /** teacher email → thoughts (Hindi + English lists) */
    private final Map<String, GoogleSheetsService.ThoughtsData> thoughtsCache = new ConcurrentHashMap<>();

    // ── Startup: load students from H2 ───────────────────────────────────────

    /**
     * On app startup, load persisted students from H2 into the in-memory cache
     * so the app is immediately ready without requiring a manual sync.
     */
    @PostConstruct
    public void loadFromDatabase() {
        List<StudentEntity> entities = studentEntityRepo.findByActiveTrue();
        if (entities.isEmpty()) {
            log.info("No students in H2 yet — waiting for first sync.");
            return;
        }

        List<Student> students = entities.stream()
                .map(e -> new Student(e.getName(), e.getGrade(),
                                      e.getStrength(), e.getWeakness(),
                                      e.getNotes(), e.isActive()))
                .toList();

        // Use a synthetic email key for single-NGO mode; real teacher email set on first sync
        settingsRepo.findAll().stream().findFirst().ifPresentOrElse(
            settings -> cache.put(settings.getEmail(), students),
            () -> cache.put("_local_", students)
        );
        log.info("Loaded {} active students from H2 into cache on startup.", students.size());
    }

    // ── Sync from Google Sheet ────────────────────────────────────────────────

    /**
     * Fetches students from Google Sheet, upserts all into H2, refreshes cache.
     * Also syncs Assembly and Thoughts tabs (config only, stored in memory).
     */
    public List<Student> syncStudents(String teacherEmail, String accessToken,
                                      String sheetId, String sheetName) {
        log.info("Syncing students for {} from sheet {}", teacherEmail, sheetId);

        // 1. Read from Google Sheet
        List<Student> all    = sheetsService.readStudents(accessToken, sheetId);
        List<Student> active = all.stream().filter(Student::isActive).toList();

        // 2. Upsert all students into H2 (save/update by name key)
        for (Student s : all) {
            StudentEntity entity = studentEntityRepo.findById(s.getName())
                    .orElseGet(() -> new StudentEntity());
            entity.setName(s.getName());
            entity.setGrade(s.getGrade());
            entity.setStrength(s.getStrength());
            entity.setWeakness(s.getWeakness());
            entity.setNotes(s.getNotes());
            entity.setActive(s.isActive());
            entity.setLastSyncedAt(Instant.now());
            studentEntityRepo.save(entity);
        }
        log.info("Upserted {} students into H2.", all.size());

        // 3. Update in-memory cache
        cache.put(teacherEmail, active);

        // 4. Persist sheet settings and sync timestamp
        TeacherSettings settings = settingsRepo.findById(teacherEmail)
                .orElseGet(() -> new TeacherSettings(teacherEmail));
        settings.setSheetId(sheetId);
        settings.setSheetName(sheetName);
        settings.setLastSync(Instant.now());
        settingsRepo.save(settings);

        // 5. Sync Assembly tab (YouTube links)
        Map<String, String> assemblyLinks = sheetsService.readAssemblyLinks(accessToken, sheetId);
        if (!assemblyLinks.isEmpty()) {
            assemblyCache.put(teacherEmail, assemblyLinks);
            log.info("Synced {} assembly links for {}", assemblyLinks.size(), teacherEmail);
        }

        // 6. Sync Thoughts tab
        GoogleSheetsService.ThoughtsData thoughts = sheetsService.readThoughts(accessToken, sheetId);
        if (!thoughts.isEmpty()) {
            thoughtsCache.put(teacherEmail, thoughts);
        }

        log.info("Sync complete: {} active students for {}", active.size(), teacherEmail);
        return active;
    }

    /**
     * Re-syncs using the previously saved sheet settings.
     */
    public List<Student> resync(String teacherEmail, String accessToken) {
        return settingsRepo.findById(teacherEmail)
                .filter(s -> s.getSheetId() != null)
                .map(s -> syncStudents(teacherEmail, accessToken, s.getSheetId(), s.getSheetName()))
                .orElse(List.of());
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns cached active students (empty list if not loaded yet). */
    public List<Student> getStudents(String teacherEmail) {
        // If no cache entry for this teacher, try loading from H2 directly
        if (!cache.containsKey(teacherEmail)) {
            List<Student> fromDb = studentEntityRepo.findByActiveTrue().stream()
                    .map(e -> new Student(e.getName(), e.getGrade(),
                                          e.getStrength(), e.getWeakness(),
                                          e.getNotes(), e.isActive()))
                    .toList();
            if (!fromDb.isEmpty()) {
                cache.put(teacherEmail, fromDb);
            }
        }
        return cache.getOrDefault(teacherEmail, List.of());
    }

    /** Returns ALL students (active + inactive) from H2 for display purposes. */
    public List<StudentEntity> getAllStudentsFromDb() {
        return studentEntityRepo.findAll();
    }

    /** Finds a student by name (case-insensitive). */
    public Optional<Student> findByName(String teacherEmail, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String query = name.trim().toLowerCase();
        return getStudents(teacherEmail).stream()
                .filter(s -> s.getName().toLowerCase().contains(query))
                .findFirst();
    }

    /** Returns true if students are loaded. */
    public boolean isReady(String teacherEmail) {
        return !getStudents(teacherEmail).isEmpty();
    }

    /** How many active students are currently cached. */
    public int count(String teacherEmail) {
        return getStudents(teacherEmail).size();
    }

    /** Assembly YouTube links for this teacher. */
    public Map<String, String> getAssemblyLinks(String teacherEmail) {
        return assemblyCache.getOrDefault(teacherEmail, Collections.emptyMap());
    }

    /** Thoughts (Hindi + English) synced from sheet. */
    public GoogleSheetsService.ThoughtsData getThoughts(String teacherEmail) {
        return thoughtsCache.getOrDefault(teacherEmail,
                new GoogleSheetsService.ThoughtsData(List.of(), List.of()));
    }
}
