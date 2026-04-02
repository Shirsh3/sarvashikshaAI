package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.QuestionResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuestionResponseRepository extends JpaRepository<QuestionResponseEntity, Long> {
    Optional<QuestionResponseEntity> findByQuestionIdAndStudentId(Long questionId, String studentId);
    List<QuestionResponseEntity> findByQuestion_QuizIdOrderByQuestion_QuestionOrderAsc(Long quizId);
    
    @Query("""
            SELECT r
            FROM QuestionResponseEntity r
            JOIN FETCH r.question qq
            LEFT JOIN FETCH r.student s
            WHERE qq.quizId = :quizId
            ORDER BY qq.questionOrder ASC
            """)
    List<QuestionResponseEntity> findForQuizWithQuestionAndStudent(@Param("quizId") Long quizId);
    void deleteByQuestion_QuizId(Long quizId);

    List<QuestionResponseEntity> findByStudentIdAndAnsweredAtIsNotNullOrderByAnsweredAtAsc(String studentId);

    @Query("SELECT COUNT(r) FROM QuestionResponseEntity r WHERE r.answeredAt IS NOT NULL")
    long countAnswered();

    @Query("SELECT COUNT(r) FROM QuestionResponseEntity r WHERE r.answeredAt IS NOT NULL AND r.isCorrect = true")
    long countAnsweredCorrect();

    @Query("""
            SELECT COUNT(r) FROM QuestionResponseEntity r
            JOIN r.question qq
            JOIN qq.quiz q
            WHERE r.answeredAt IS NOT NULL
            AND r.answeredAt >= :from AND r.answeredAt < :to
            AND (:grade IS NULL OR q.grade = :grade)
            """)
    long countAnsweredBetweenForGrade(@Param("from") Instant from, @Param("to") Instant to, @Param("grade") String grade);

    @Query("""
            SELECT COUNT(r) FROM QuestionResponseEntity r
            JOIN StudentEntity s ON s.code = r.studentId
            JOIN r.question qq
            JOIN qq.quiz q
            WHERE r.answeredAt IS NOT NULL
            AND r.answeredAt >= :from AND r.answeredAt < :to
            AND s.active = true
            AND (:grade IS NULL OR q.grade = :grade)
            """)
    long countAnsweredBetweenForActiveStudentsGrade(@Param("from") Instant from, @Param("to") Instant to, @Param("grade") String grade);

    @Query("""
            SELECT r.studentId,
                   COALESCE(SUM(r.marksAwarded), 0),
                   COUNT(r),
                   SUM(CASE WHEN r.isCorrect = true THEN 1 ELSE 0 END)
            FROM QuestionResponseEntity r
            WHERE r.answeredAt IS NOT NULL
            GROUP BY r.studentId
            ORDER BY COALESCE(SUM(r.marksAwarded), 0) DESC
            """)
    List<Object[]> aggregateLeaderboardByStudent();

    @Query("""
            SELECT r.studentId,
                   COALESCE(SUM(r.marksAwarded), 0),
                   COUNT(r),
                   SUM(CASE WHEN r.isCorrect = true THEN 1 ELSE 0 END)
            FROM QuestionResponseEntity r
            WHERE r.answeredAt IS NOT NULL
            AND r.answeredAt >= :from AND r.answeredAt < :to
            GROUP BY r.studentId
            ORDER BY COALESCE(SUM(r.marksAwarded), 0) DESC
            """)
    List<Object[]> aggregateLeaderboardByStudentBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COALESCE(q.subject, 'General'),
                   AVG(CASE WHEN r.isCorrect = true THEN 100.0 ELSE 0.0 END)
            FROM QuestionResponseEntity r
            JOIN r.question qq
            JOIN qq.quiz q
            WHERE r.studentId = :code AND r.answeredAt IS NOT NULL
            GROUP BY COALESCE(q.subject, 'General')
            ORDER BY AVG(CASE WHEN r.isCorrect = true THEN 100.0 ELSE 0.0 END) DESC
            """)
    List<Object[]> averageAccuracyBySubjectForStudent(@Param("code") String code);

    @Query("""
            SELECT COUNT(r) FROM QuestionResponseEntity r
            JOIN StudentEntity s ON s.code = r.studentId
            WHERE r.answeredAt IS NOT NULL
            AND r.answeredAt >= :from AND r.answeredAt < :to
            AND s.active = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countAnsweredBetweenForStudentGrade(@Param("from") Instant from, @Param("to") Instant to, @Param("grade") String grade);

    @Query("""
            SELECT COUNT(r) FROM QuestionResponseEntity r
            JOIN StudentEntity s ON s.code = r.studentId
            WHERE r.answeredAt IS NOT NULL AND r.isCorrect = true
            AND r.answeredAt >= :from AND r.answeredAt < :to
            AND s.active = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countAnsweredCorrectBetweenForStudentGrade(@Param("from") Instant from, @Param("to") Instant to, @Param("grade") String grade);

    /** Admin analytics overview: answered items, optional grade filter on student roster */
    @Query("""
            SELECT COUNT(r) FROM QuestionResponseEntity r
            JOIN StudentEntity s ON s.code = r.studentId
            WHERE r.answeredAt IS NOT NULL AND s.active = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countAnsweredForGradeFilter(@Param("grade") String grade);

    @Query("""
            SELECT COUNT(r) FROM QuestionResponseEntity r
            JOIN StudentEntity s ON s.code = r.studentId
            WHERE r.answeredAt IS NOT NULL AND r.isCorrect = true AND s.active = true
            AND (:grade IS NULL OR s.grade = :grade)
            """)
    long countCorrectForGradeFilter(@Param("grade") String grade);
}
