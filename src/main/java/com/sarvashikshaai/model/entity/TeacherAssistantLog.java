package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Stores the last N Teacher AI Assistant outputs so the teacher can review them.
 */
@Entity
@Table(name = "teacher_assistant_log")
@Getter @Setter @NoArgsConstructor
public class TeacherAssistantLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_type", length = 100)
    private String templateType;

    @Column(name = "prompt_summary", length = 500)
    private String promptSummary;

    @Column(name = "file_name", length = 300)
    private String fileName;

    @Column(name = "output_text", length = 8000)
    private String outputText;

    @Column(name = "created_at")
    private Instant createdAt;

    public TeacherAssistantLog(String templateType, String promptSummary,
                                String fileName, String outputText) {
        this.templateType  = templateType;
        this.promptSummary = promptSummary;
        this.fileName      = fileName;
        this.outputText    = outputText;
        this.createdAt     = Instant.now();
    }
}
