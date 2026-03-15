package com.sarvashikshaai.model.dto;

import java.util.List;

public record OpenAiChatRequest(String model, List<OpenAiMessage> messages) {}
