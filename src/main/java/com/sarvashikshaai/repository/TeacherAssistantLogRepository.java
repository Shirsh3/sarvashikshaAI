package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.TeacherAssistantLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeacherAssistantLogRepository extends JpaRepository<TeacherAssistantLog, Long> {
    List<TeacherAssistantLog> findTop10ByOrderByCreatedAtDesc();
}
