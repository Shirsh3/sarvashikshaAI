package ai.sarvasiksha.web;

import ai.sarvasiksha.config.OpenAiProperties;
import ai.sarvasiksha.dto.AskRequest;
import ai.sarvasiksha.dto.AskResponse;
import ai.sarvasiksha.service.OpenAiTeacherService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Validated
public class AskController {

    private final OpenAiTeacherService teacherService;
    private final OpenAiProperties openAiProperties;

    public AskController(OpenAiTeacherService teacherService, OpenAiProperties openAiProperties) {
        this.teacherService = teacherService;
        this.openAiProperties = openAiProperties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "sarvashiksha-ai");
        body.put("openaiConfigured",
                openAiProperties.getApiKey() != null && !openAiProperties.getApiKey().isBlank());
        return body;
    }

    /**
     * Ask the AI teacher. Alexa skill backend can call this with the transcribed utterance.
     *
     * <pre>
     * POST /api/v1/ask
     * { "question": "What is photosynthesis?", "locale": "en-IN" }
     * </pre>
     */
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        String answer = teacherService.ask(request.getQuestion(), request.getLocale());
        return ResponseEntity.ok(new AskResponse(answer, openAiProperties.getModel()));
    }
}
