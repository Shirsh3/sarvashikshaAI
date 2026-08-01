package ai.sarvasiksha.dto;

import javax.validation.constraints.NotBlank;

public class AskRequest {

    @NotBlank(message = "question is required")
    private String question;

    /** Optional locale hint, e.g. hi-IN or en-IN (Alexa can pass this later). */
    private String locale;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
