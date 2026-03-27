package com.sarvashikshaai.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiChatRequest(
        String model,
        List<OpenAiMessage> messages,
        Double temperature,
        @JsonProperty("top_p") Double topP
) {}
