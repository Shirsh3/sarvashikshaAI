package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.ReadingFeedbackRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ReadingFeedbackRepository extends JpaRepository<ReadingFeedbackRecord, Long> {

    List<ReadingFeedbackRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ReadingFeedbackRecord> findByStudentNameOrderByCreatedAtDesc(String studentName);

    List<ReadingFeedbackRecord> findByStudentNameOrderByCreatedAtAsc(String studentName);

    List<ReadingFeedbackRecord> findByDateOrderByStudentNameAsc(LocalDate date);

    @Query("""
            SELECT COUNT(r) FROM ReadingFeedbackRecord r
            JOIN StudentEntity s ON s.name = r.studentName
            WHERE r.createdAt >= :from AND r.createdAt < :to
            AND s.active = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countCreatedBetweenForGrade(@Param("from") Instant from, @Param("to") Instant to, @Param("grade") String grade);

    /** Leaderboard: per-student session count and avg accuracy. Rows: [0]=studentName, [1]=count, [2]=avgAccuracyPercent */
    @Query("""
            SELECT r.studentName,
                   COUNT(r),
                   AVG(COALESCE(r.accuracyPercent, 0))
            FROM ReadingFeedbackRecord r
            JOIN StudentEntity s ON s.name = r.studentName
            WHERE r.createdAt >= :from AND r.createdAt < :to
            AND s.active = true
            GROUP BY r.studentName
            """)
    List<Object[]> aggregateReadingByStudentBetween(@Param("from") Instant from, @Param("to") Instant to);
}
