package com.sarvashikshaai.boundary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExplanationBoundary {

    private final int maxSentences;
    private final String complexityHint;
    private final boolean requireExample;
}

