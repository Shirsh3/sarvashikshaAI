package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.StudentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StudentEntityRepository extends JpaRepository<StudentEntity, String> {
    List<StudentEntity> findByActiveTrue();

    StudentEntity findByCode(String code);

    @Query("SELECT COUNT(s) FROM StudentEntity s WHERE s.active = true AND (:grade IS NULL OR s.grade = :grade)")
    long countActiveForGrade(@Param("grade") String grade);

    @Query("SELECT DISTINCT s.grade FROM StudentEntity s WHERE s.active = true AND s.grade IS NOT NULL AND TRIM(s.grade) <> '' ORDER BY s.grade")
    List<String> findDistinctActiveGrades();

    /** Prefix match on name or student code (case-insensitive), optional grade filter — same idea as teacher setup search. */
    @Query("""
            SELECT s FROM StudentEntity s
            WHERE s.active = true
            AND (
                LOWER(s.name) LIKE LOWER(CONCAT(:prefix, '%'))
                OR LOWER(COALESCE(s.code, '')) LIKE LOWER(CONCAT(:prefix, '%'))
            )
            AND (:grade IS NULL OR s.grade = :grade)
            ORDER BY s.name
            """)
    List<StudentEntity> searchActiveByNamePrefix(@Param("prefix") String prefix, @Param("grade") String grade, Pageable pageable);

    /**
     * Inactive students cleanup: only delete students already marked inactive AND with no attendance
     * recorded on/after the cutoff date. This avoids deleting currently-used students.
     */
    @Query("""
            SELECT COUNT(s) FROM StudentEntity s
            WHERE s.active = false
            AND NOT EXISTS (
                SELECT 1 FROM AttendanceRecord a
                WHERE a.studentName = s.name
                AND a.date >= :cutoff
            )
            """)
    long countInactiveWithNoRecentAttendance(@Param("cutoff") LocalDate cutoff);

    @Modifying
    @Query("""
            DELETE FROM StudentEntity s
            WHERE s.active = false
            AND NOT EXISTS (
                SELECT 1 FROM AttendanceRecord a
                WHERE a.studentName = s.name
                AND a.date >= :cutoff
            )
            """)
    int deleteInactiveWithNoRecentAttendance(@Param("cutoff") LocalDate cutoff);
}
