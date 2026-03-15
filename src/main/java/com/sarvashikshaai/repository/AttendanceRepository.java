package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByDateOrderByStudentNameAsc(LocalDate date);
    Optional<AttendanceRecord> findByStudentNameAndDate(String studentName, LocalDate date);
    List<AttendanceRecord> findByDateBetweenOrderByDateAscStudentNameAsc(LocalDate from, LocalDate to);
}
