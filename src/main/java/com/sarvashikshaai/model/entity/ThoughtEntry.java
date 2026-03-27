package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "thought_entries")
@Getter
@Setter
@NoArgsConstructor
public class ThoughtEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "language", length = 5)
    private String language; // "hi" or "en"

    @Column(name = "thought_hi", length = 1000)
    private String thoughtHi;

    @Column(name = "thought_en", length = 1000)
    private String thoughtEn;

    @Column(name = "word_english", length = 255)
    private String wordEnglish;

    @Column(name = "word_hindi", length = 255)
    private String wordHindi;

    @Column(name = "habit", length = 1000)
    private String habit;

    @Column(name = "shown")
    private Boolean shown;
}

