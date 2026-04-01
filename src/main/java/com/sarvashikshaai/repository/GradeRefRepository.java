package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.GradeRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeRefRepository extends JpaRepository<GradeRef, String> {
    List<GradeRef> findAllByOrderBySortOrderAsc();
}
