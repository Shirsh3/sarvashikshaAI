package com.sarvashikshaai.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeachingRequest {

    @NotBlank
    private String topic;

    /** Optional context flags used by PromptBuilder and services. */
    private String classSessionMode; // e.g. live | quiz
    private String prepareFocus;     // e.g. revision
    private String prepareGrade;     // e.g. 2, 5, 8
    /** Optional subject filter for class-PDF RAG (CHEMISTRY, MATH, …). */
    private String prepareSubject;

    /** Optional chapter/PDF id — scopes RAG to one uploaded material. */
    private Long prepareMaterialId;

    /** Learning teaching session — enables conversation memory. */
    private boolean learningMode;

    /** Existing conversation id (optional); created if missing when learningMode. */
    private Long learningConversationId;

    /** Chapter title for teaching indicator / prompts. */
    private String chapterTitle;

    /** auto | en | hi */
    private String language;

    /** simple | detailed */
    private String explanationLevel;

    /** Set by API controller from JWT principal. */
    private String teacherUsername;

    /** When explaining from a quiz question, we store/fetch a cache keyed by this ID. */
    private Long sourceQuestionId;

    /** If true, backend may fetch YouTube videoId; otherwise it will not call YouTube. */
    private boolean includeVideo;
}
