package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.ClassMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassMaterialRepository extends JpaRepository<ClassMaterialEntity, Long> {

    List<ClassMaterialEntity> findAllByOrderByCreatedAtDesc();

    List<ClassMaterialEntity> findByGradeOrderByCreatedAtDesc(String grade);

    List<ClassMaterialEntity> findByGradeAndStatus(String grade, String status);

    List<ClassMaterialEntity> findByStatusOrderByTitleAsc(String status);

    List<ClassMaterialEntity> findByGradeAndStatusOrderByTitleAsc(String grade, String status);

    List<ClassMaterialEntity> findByGradeAndSubjectIgnoreCaseAndStatusOrderByTitleAsc(
            String grade, String subject, String status);
}
