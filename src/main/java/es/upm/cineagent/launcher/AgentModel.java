package es.upm.cineagent.launcher;

public enum AgentModel {

    USER("UserAgent"),
    SEARCH("SearchAgent"),
    PROCESSING("ProcessingAgent"),
    DISPLAY("DisplayAgent"),
    CRITIC("CriticAgent"),
    JURY("JuryAgent"),
    DESCONOCIDO("Desconocido");
    private final String value;

    AgentModel(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static AgentModel getEnum(String value) {
        switch (value) {
            case "UserAgent":       return USER;
            case "SearchAgent":     return SEARCH;
            case "ProcessingAgent": return PROCESSING;
            case "DisplayAgent":    return DISPLAY;
            case "CriticAgent":     return CRITIC;
            case "JuryAgent":       return JURY;
            default:                return DESCONOCIDO;
        }
    }
}
