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

    /** When explaining from a quiz question, we store/fetch a cache keyed by this ID. */
    private Long sourceQuestionId;

    /** If true, backend may fetch YouTube videoId; otherwise it will not call YouTube. */
    private boolean includeVideo;
}
