package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.ThoughtEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ThoughtEntryRepository extends JpaRepository<ThoughtEntry, Long> {

    List<ThoughtEntry> findByShownFalse();

    List<ThoughtEntry> findByShownFalseAndLanguage(String language);

    @Query("SELECT COUNT(t) FROM ThoughtEntry t")
    long countAll();

    @Modifying
    @Query("DELETE FROM ThoughtEntry t")
    int deleteAllFast();
}

