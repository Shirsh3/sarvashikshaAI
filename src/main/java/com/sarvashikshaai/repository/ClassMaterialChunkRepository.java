package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.ClassMaterialChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClassMaterialChunkRepository extends JpaRepository<ClassMaterialChunkEntity, Long> {

    List<ClassMaterialChunkEntity> findByMaterialIdOrderByChunkIndexAsc(Long materialId);

    void deleteByMaterialId(Long materialId);

    @Query("""
            select c from ClassMaterialChunkEntity c
            join ClassMaterialEntity m on m.id = c.materialId
            where m.status = 'READY'
              and (:grade is null or :grade = '' or m.grade = :grade)
              and (:subject is null or :subject = '' or upper(m.subject) = upper(:subject))
              and (:materialId is null or c.materialId = :materialId)
              and c.embedding is not null
            """)
    List<ClassMaterialChunkEntity> findReadyChunks(
            @Param("grade") String grade,
            @Param("subject") String subject,
            @Param("materialId") Long materialId);
}
