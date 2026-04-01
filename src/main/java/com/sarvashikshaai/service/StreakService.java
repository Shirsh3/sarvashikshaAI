package com.sarvashikshaai.service;

import com.sarvashikshaai.model.dto.StreakLevel;
import com.sarvashikshaai.model.dto.StreakResponse;
import com.sarvashikshaai.model.dto.StudentActivity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class StreakService {

    private static final EnumMap<StreakLevel, List<String>> EN_MESSAGES = new EnumMap<>(StreakLevel.class);

    static {
        EN_MESSAGES.put(StreakLevel.NONE, List.of(
                "Start learning today to build your streak.",
                "You don't have a streak yet—begin with a small daily practice.",
                "Let's restart your learning routine today."
        ));

        EN_MESSAGES.put(StreakLevel.STARTED, List.of(
                "Good start! Keep the momentum going.",
                "Nice beginning, try to continue your streak tomorrow.",
                "You're on track, stay consistent!"
        ));

        EN_MESSAGES.put(StreakLevel.CONSISTENT, List.of(
                "Great consistency! Keep learning daily.",
                "You're building a strong habit.",
                "Keep up the good work!"
        ));

        EN_MESSAGES.put(StreakLevel.STRONG, List.of(
                "You're doing great—your streak is getting strong.",
                "Strong streak! Keep practicing every day.",
                "Excellent work—stay focused for the next days."
        ));

        EN_MESSAGES.put(StreakLevel.CHAMPION, List.of(
                "Champion streak! You're a daily achiever.",
                "Amazing! Keep up this unstoppable momentum.",
                "Unreal consistency—keep learning and growing!"
        ));
    }

    /**
     * Current streak is the number of consecutive days ending at (today) where:
     * - If student has activity today, the streak starts from today.
     * - Otherwise, it starts from yesterday.
     * - The streak ends when a day with no activity is found.
     *
     * Future dates are ignored.
     */
    public int calculateCurrentStreak(List<StudentActivity> activities) {
        if (activities == null || activities.isEmpty()) return 0;

        LocalDate now = LocalDate.now();
        Set<LocalDate> uniqueDays = toUniqueDays(activities, now);
        if (uniqueDays.isEmpty()) return 0;

        LocalDate start = uniqueDays.contains(now) ? now : now.minusDays(1);
        if (!uniqueDays.contains(start)) return 0;

        int streak = 0;
        LocalDate cursor = start;
        while (uniqueDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public StreakResponse generateStreakResponse(List<StudentActivity> activities) {
        int streak = calculateCurrentStreak(activities);
        StreakLevel level = StreakLevel.getStreakLevel(streak);
        String message = pickMessage(level);
        return new StreakResponse(streak, level, message);
    }

    // ---------------- Optional Bonus APIs ----------------

    /**
     * Longest streak anywhere in the given history.
     * Future dates are ignored.
     */
    public int calculateLongestStreak(List<StudentActivity> activities) {
        if (activities == null || activities.isEmpty()) return 0;
        LocalDate now = LocalDate.now();
        Set<LocalDate> uniqueDays = toUniqueDays(activities, now);
        if (uniqueDays.isEmpty()) return 0;

        int best = 0;
        for (LocalDate d : uniqueDays) {
            // Start only at the beginning of a consecutive run.
            if (!uniqueDays.contains(d.minusDays(1))) {
                int run = 0;
                LocalDate cursor = d;
                while (uniqueDays.contains(cursor)) {
                    run++;
                    cursor = cursor.plusDays(1);
                }
                best = Math.max(best, run);
            }
        }
        return best;
    }

    /**
     * Current weekly streak: consecutive weeks (ISO weeks) ending at the week of today
     * where at least one activity exists in each week.
     */
    public int calculateCurrentWeeklyStreak(List<StudentActivity> activities) {
        if (activities == null || activities.isEmpty()) return 0;
        LocalDate now = LocalDate.now();
        Set<LocalDate> uniqueDays = toUniqueDays(activities, now);
        if (uniqueDays.isEmpty()) return 0;

        WeekFields wf = WeekFields.ISO;
        int currentWeek = now.get(wf.weekOfWeekBasedYear());
        int currentYear = now.get(wf.weekBasedYear());

        int streak = 0;
        int week = currentWeek;
        int year = currentYear;

        while (true) {
            boolean hasActivityThisWeek = hasActivityInWeek(uniqueDays, wf, week, year);
            if (!hasActivityThisWeek) break;
            streak++;

            // Move to previous week
            // easier: take a date within target week then minus 7 days.
            LocalDate anyDateInWeek = pickAnyDateInWeek(uniqueDays, wf, week, year);
            LocalDate prevDate = (anyDateInWeek != null) ? anyDateInWeek.minusDays(7) : now.minusDays(streak * 7L);
            week = prevDate.get(wf.weekOfWeekBasedYear());
            year = prevDate.get(wf.weekBasedYear());
        }

        return streak;
    }

    // ---------------- Internal helpers ----------------

    private static Set<LocalDate> toUniqueDays(List<StudentActivity> activities, LocalDate now) {
        Set<LocalDate> days = new HashSet<>();
        for (StudentActivity a : activities) {
            if (a == null) continue;
            if (a.activityDate() == null) continue;
            if (a.activityDate().isAfter(now)) continue; // ignore future
            days.add(a.activityDate());
        }
        return days;
    }

    private static String pickMessage(StreakLevel level) {
        Objects.requireNonNull(level, "level must not be null");
        List<String> options = EN_MESSAGES.get(level);
        if (options == null || options.isEmpty()) {
            return "";
        }
        int idx = ThreadLocalRandom.current().nextInt(options.size());
        return options.get(idx);
    }

    private static boolean hasActivityInWeek(Set<LocalDate> uniqueDays, WeekFields wf, int week, int year) {
        for (LocalDate d : uniqueDays) {
            int w = d.get(wf.weekOfWeekBasedYear());
            int y = d.get(wf.weekBasedYear());
            if (w == week && y == year) return true;
        }
        return false;
    }

    private static LocalDate pickAnyDateInWeek(Set<LocalDate> uniqueDays, WeekFields wf, int week, int year) {
        for (LocalDate d : uniqueDays) {
            int w = d.get(wf.weekOfWeekBasedYear());
            int y = d.get(wf.weekBasedYear());
            if (w == week && y == year) return d;
        }
        return null;
    }

    // ---------------- Example usage ----------------

    public StreakResponse exampleUsage() {
        LocalDate today = LocalDate.now();
        List<StudentActivity> activities = List.of(
                new StudentActivity("s1", today.minusDays(3), StudentActivity.Type.QUIZ),
                new StudentActivity("s1", today.minusDays(2), StudentActivity.Type.QUIZ),
                new StudentActivity("s1", today.minusDays(1), StudentActivity.Type.ATTENDANCE),
                new StudentActivity("s1", today, StudentActivity.Type.READING)
        );
        return generateStreakResponse(activities);
    }
}

