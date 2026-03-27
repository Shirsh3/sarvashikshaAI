package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.ThoughtEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThoughtEntryRepository extends JpaRepository<ThoughtEntry, Long> {

    List<ThoughtEntry> findByShownFalse();

    List<ThoughtEntry> findByShownFalseAndLanguage(String language);
}

