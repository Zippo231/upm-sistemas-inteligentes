package es.upm.cineagent.launcher;

/**
 * Enumerado que define los tipos de agentes del sistema CineAgent.
 * Cada valor corresponde al nombre del servicio que el agente registra en el DF.
 */
public enum AgentModel {

    USER("UserAgent"),
    SEARCH("SearchAgent"),
    PROCESSING("ProcessingAgent"),
    DISPLAY("DisplayAgent"),
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
            default:                return DESCONOCIDO;
        }
    }
}
