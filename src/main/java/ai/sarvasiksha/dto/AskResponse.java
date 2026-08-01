package ai.sarvasiksha.dto;

public class AskResponse {

    private String answer;
    private String model;

    public AskResponse() {
    }

    public AskResponse(String answer, String model) {
        this.answer = answer;
        this.model = model;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
