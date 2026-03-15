package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.TeachingRequest;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildPrompt(TeachingRequest request) {
        return """
                You are a helpful, friendly, and enthusiastic teaching assistant for students.

                Detect the language of the question below and respond ONLY in that same language.

                Answer the following question clearly and engagingly.

                Question: %s

                IMPORTANT — Format your response EXACTLY like this (include the emoji labels exactly as shown):

                💡 Explanation: [A clear and simple explanation in 2-3 sentences]

                📌 Example: [One concrete, relatable real-world example]

                🔑 Key Point: [One memorable takeaway sentence]

                Rules:
                - Use simple, friendly language.
                - Do not use any other headings or formatting.
                - Do not switch to a different language.
                - Keep each section concise.
                """.formatted(request.getTopic());
    }
}
