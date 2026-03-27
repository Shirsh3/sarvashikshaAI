package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentEntityRepository extends JpaRepository<StudentEntity, String> {
    List<StudentEntity> findByActiveTrue();

    StudentEntity findByCode(String code);
}
