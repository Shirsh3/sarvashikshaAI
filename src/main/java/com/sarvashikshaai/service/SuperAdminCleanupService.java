package com.sarvashikshaai.service;

import com.sarvashikshaai.model.entity.CleanupAuditRecord;
import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.repository.CleanupAuditRepository;
import com.sarvashikshaai.repository.StudentEntityRepository;
import com.sarvashikshaai.repository.ThoughtEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SuperAdminCleanupService {

    private final AttendanceRepository attendanceRepository;
    private final ThoughtEntryRepository thoughtEntryRepository;
    private final StudentEntityRepository studentEntityRepository;
    private final CleanupAuditRepository cleanupAuditRepository;

    public long previewOldAttendance(int days) {
        int safeDays = requireSafeDays(days, 7, 3650);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);
        return attendanceRepository.countByDateBefore(cutoff);
    }

    public long previewTempData() {
        return thoughtEntryRepository.countAll();
    }

    public long previewInactiveStudents(int days) {
        int safeDays = requireSafeDays(days, 30, 3650);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);
        return studentEntityRepository.countInactiveWithNoRecentAttendance(cutoff);
    }

    @Transactional
    public long deleteOldAttendance(String actorUsername, int days) {
        int safeDays = requireSafeDays(days, 7, 3650);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        long toDelete = attendanceRepository.countByDateBefore(cutoff);
        if (toDelete <= 0) {
            audit(actorUsername, "cleanup.old_attendance", Map.of("days", safeDays), 0);
            return 0;
        }

        int deleted = attendanceRepository.deleteByDateBefore(cutoff);
        audit(actorUsername, "cleanup.old_attendance", Map.of("days", safeDays), deleted);
        return deleted;
    }

    @Transactional
    public long deleteTempData(String actorUsername) {
        long toDelete = thoughtEntryRepository.countAll();
        if (toDelete <= 0) {
            audit(actorUsername, "cleanup.temp_data", Map.of(), 0);
            return 0;
        }
        int deleted = thoughtEntryRepository.deleteAllFast();
        audit(actorUsername, "cleanup.temp_data", Map.of(), deleted);
        return deleted;
    }

    @Transactional
    public long deleteInactiveStudents(String actorUsername, int days) {
        int safeDays = requireSafeDays(days, 30, 3650);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        long toDelete = studentEntityRepository.countInactiveWithNoRecentAttendance(cutoff);
        if (toDelete <= 0) {
            audit(actorUsername, "cleanup.inactive_students", Map.of("days", safeDays), 0);
            return 0;
        }

        int deleted = studentEntityRepository.deleteInactiveWithNoRecentAttendance(cutoff);
        audit(actorUsername, "cleanup.inactive_students", Map.of("days", safeDays), deleted);
        return deleted;
    }

    private void audit(String actorUsername, String actionKey, Map<String, Object> params, long deletedCount) {
        CleanupAuditRecord r = new CleanupAuditRecord();
        r.setActorUsername(actorUsername == null || actorUsername.isBlank() ? "unknown" : actorUsername);
        r.setActionKey(actionKey);
        r.setParamsJson(params == null || params.isEmpty() ? null : params.toString());
        r.setDeletedCount(deletedCount);
        cleanupAuditRepository.save(r);
    }

    private static int requireSafeDays(int days, int min, int max) {
        if (days < min || days > max) {
            throw new IllegalArgumentException("days must be between " + min + " and " + max);
        }
        return days;
    }
}

