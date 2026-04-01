package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByDateOrderByStudentNameAsc(LocalDate date);
    long countByDate(LocalDate date);
    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE a.date = :date
            AND s.active = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countByDateAndGrade(@Param("date") LocalDate date, @Param("grade") String grade);
    Optional<AttendanceRecord> findByStudentNameAndDate(String studentName, LocalDate date);
    List<AttendanceRecord> findByDateBetweenOrderByDateAscStudentNameAsc(LocalDate from, LocalDate to);

    long countByStudentName(String studentName);

    long countByStudentNameAndPresentTrue(String studentName);

    long countByPresentTrue();

    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true
            AND a.date >= :from AND a.date <= :to
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countByDateRangeAndGrade(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("grade") String grade);

    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true
            AND a.present = true
            AND a.date >= :from AND a.date <= :to
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countPresentByDateRangeAndGrade(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("grade") String grade);

    /** One row per day: [0]=date, [1]=present percent 0–100 */
    @Query("""
            SELECT a.date,
                   SUM(CASE WHEN a.present = true THEN 1 ELSE 0 END) * 100.0 / COUNT(a)
            FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true
            AND a.date >= :from AND a.date <= :to
            AND (:grade IS NULL OR s.grade = :grade)
            GROUP BY a.date
            ORDER BY a.date
            """)
    List<Object[]> dailyPresentPercentByGrade(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("grade") String grade);

    /** Admin analytics overview: total marks and present count, optional grade filter */
    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countAttendanceMarksForGradeFilter(@Param("grade") String grade);

    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true AND a.present = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countPresentAttendanceMarksForGradeFilter(@Param("grade") String grade);

    @Query("""
            SELECT a.date
            FROM AttendanceRecord a
            WHERE a.studentName = :studentName
            AND a.present = true
            ORDER BY a.date ASC
            """)
    List<LocalDate> findActivityDatesByStudentNameOrderByDateAsc(@Param("studentName") String studentName);

    // Active-only per-student analytics helpers
    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true
            AND a.studentName = :studentName
            """)
    long countByActiveStudentName(@Param("studentName") String studentName);

    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true
            AND a.studentName = :studentName
            AND a.present = true
            """)
    long countPresentByActiveStudentName(@Param("studentName") String studentName);

    @Query("""
            SELECT a.date
            FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE s.active = true
            AND a.studentName = :studentName
            AND a.present = true
            ORDER BY a.date ASC
            """)
    List<LocalDate> findActivityDatesByActiveStudentNameOrderByDateAsc(@Param("studentName") String studentName);

    /** Leaderboard: per-student present days between (inclusive). Returns rows: [0]=studentName, [1]=presentCount */
    @Query("""
            SELECT a.studentName, COUNT(a)
            FROM AttendanceRecord a
            JOIN StudentEntity s ON s.name = a.studentName
            WHERE a.present = true
            AND s.active = true
            AND a.date >= :from AND a.date <= :to
            GROUP BY a.studentName
            """)
    List<Object[]> countPresentDaysByStudentBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByDateBefore(LocalDate cutoff);

    @Modifying
    @Query("DELETE FROM AttendanceRecord a WHERE a.date < :cutoff")
    int deleteByDateBefore(@Param("cutoff") LocalDate cutoff);
}
