package com.sarvashikshaai.service;

import com.sarvashikshaai.model.entity.ClassMaterialEntity;
import com.sarvashikshaai.repository.ClassMaterialChunkRepository;
import com.sarvashikshaai.repository.ClassMaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassMaterialService {

    private final ClassMaterialRepository materialRepository;
    private final ClassMaterialChunkRepository chunkRepository;
    private final FileExtractionService fileExtractionService;
    private final ClassMaterialIndexingService indexingService;

    @Value("${sarva.materials.storage-dir:${user.home}/.sarvashikshaai/materials}")
    private String storageDir;

    @Transactional
    public ClassMaterialEntity createPendingUpload(
            MultipartFile file,
            String grade,
            String subject,
            String title,
            String uploadedBy
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is required");
        }
        if (grade == null || grade.isBlank()) {
            throw new IllegalArgumentException("Grade is required");
        }
        var type = fileExtractionService.detectType(file);
        if (type != FileExtractionService.FileType.PDF) {
            throw new IllegalArgumentException("Only PDF uploads are supported");
        }

        Instant now = Instant.now();
        ClassMaterialEntity m = new ClassMaterialEntity();
        m.setGrade(grade.trim());
        m.setSubject(EmbeddingSearchService.normalizeSubject(subject));
        String original = file.getOriginalFilename() == null ? "material.pdf" : file.getOriginalFilename();
        m.setOriginalFilename(original);
        m.setTitle((title == null || title.isBlank()) ? stripExt(original) : title.trim());
        m.setStatus(ClassMaterialEntity.STATUS_PENDING);
        m.setChunkCount(0);
        m.setUploadedBy(uploadedBy);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        m = materialRepository.save(m);

        Path dir = Path.of(storageDir);
        Files.createDirectories(dir);
        Path dest = dir.resolve(m.getId() + ".pdf");
        Files.write(dest, file.getBytes());
        m.setStoragePath(dest.toAbsolutePath().toString());
        m.setUpdatedAt(Instant.now());
        return materialRepository.save(m);
    }

    public void startIndexing(Long materialId) {
        indexingService.indexAsync(materialId);
    }

    @Transactional
    public void delete(Long id) {
        materialRepository.findById(id).ifPresent(m -> {
            chunkRepository.deleteByMaterialId(id);
            if (m.getStoragePath() != null) {
                try {
                    Files.deleteIfExists(Path.of(m.getStoragePath()));
                } catch (IOException e) {
                    log.warn("Could not delete file {}: {}", m.getStoragePath(), e.getMessage());
                }
            }
            materialRepository.delete(m);
        });
    }

    public List<ClassMaterialEntity> listAll() {
        return materialRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ClassMaterialEntity> listByGrade(String grade) {
        if (grade == null || grade.isBlank()) return listAll();
        return materialRepository.findByGradeOrderByCreatedAtDesc(grade.trim());
    }

    /** READY materials for Learning chapter picker (optional grade + subject). */
    public List<ClassMaterialEntity> listReady(String grade, String subject) {
        String g = grade == null ? "" : grade.trim();
        boolean hasSubject = subject != null && !subject.isBlank();
        String s = hasSubject ? EmbeddingSearchService.normalizeSubject(subject) : "";
        if (!g.isEmpty() && hasSubject) {
            return materialRepository.findByGradeAndSubjectIgnoreCaseAndStatusOrderByTitleAsc(
                    g, s, ClassMaterialEntity.STATUS_READY);
        }
        if (!g.isEmpty()) {
            return materialRepository.findByGradeAndStatusOrderByTitleAsc(g, ClassMaterialEntity.STATUS_READY);
        }
        return materialRepository.findByStatusOrderByTitleAsc(ClassMaterialEntity.STATUS_READY);
    }

    private static String stripExt(String name) {
        int dot = name.toLowerCase(Locale.ROOT).lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
