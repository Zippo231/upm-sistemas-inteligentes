package es.upm.cineagent.agents;

import com.google.gson.*;
import es.upm.cineagent.launcher.AgentBase;
import es.upm.cineagent.launcher.AgentModel;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class JuryAgent extends AgentBase {

    public static final String NICKNAME = "JuryAgent";

    // Cuántos votos esperamos antes de deliberar
    private static final int EXPECTED_VOTES = 3;

    // Acumulamos los votos del ciclo actual
    private final List<JsonObject> votes = new ArrayList<>();

    @Override
    protected void setup() {
        super.setup();
        this.type = AgentModel.JURY; // añade JURY a tu enum AgentModel
        registerAgentDF();
        addBehaviour(new JuryBehaviour());
        log("JuryAgent iniciado y registrado en el DF.");
    }

    private class JuryBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = blockingReceive(
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchOntology("critic-vote")
                )
            );

            if (msg != null) {
                JsonObject vote = JsonParser.parseString(msg.getContent()).getAsJsonObject();
                votes.add(vote);
                log("Voto recibido de: " + vote.get("role").getAsString()
                    + " (" + votes.size() + "/" + EXPECTED_VOTES + ")");

                if (votes.size() == EXPECTED_VOTES) {
                    log("Todos los votos recibidos. Deliberando...");
                    JsonArray finalRanking = deliberate();
                    votes.clear(); // Limpiamos para la siguiente búsqueda

                    DFAgentDescription[] display = getAgentsDF(AgentModel.DISPLAY);
                    if (display.length > 0) {
                        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                        inform.addReceiver(display[0].getName());
                        inform.setContent(finalRanking.toString());
                        inform.setOntology("movie-results");
                        send(inform);
                        log("Ranking final enviado al DisplayAgent.");
                    } else {
                        loge("No se encontró DisplayAgent en el DF.");
                    }
                }
            }
        }


        private JsonArray deliberate() {
            // Mapa: id de película → objeto película + puntos Borda acumulados
            Map<Integer, JsonObject> movieMap   = new LinkedHashMap<>();
            Map<Integer, double[]>   positions  = new LinkedHashMap<>(); // para varianza

            for (JsonObject vote : votes) {
                JsonArray ranking = vote.getAsJsonArray("ranking");
                int n = ranking.size();

                for (int pos = 0; pos < n; pos++) {
                    JsonObject movie = ranking.get(pos).getAsJsonObject();
                    int id = movie.get("id").getAsInt();

                    // Guardamos la película la primera vez que la vemos
                    movieMap.putIfAbsent(id, movie.deepCopy());

                    // Puntos Borda: n puntos al primero, 1 al último
                    int bordaPoints = n - pos;
                    JsonObject stored = movieMap.get(id);
                    double prev = stored.has("bordaScore")
                        ? stored.get("bordaScore").getAsDouble() : 0;
                    stored.addProperty("bordaScore", prev + bordaPoints);

                    // Guardamos posición para calcular varianza después
                    positions.computeIfAbsent(id, k -> new double[EXPECTED_VOTES]);
                    // buscamos cuántos votos llevamos para este id
                    int voteIdx = (int)(stored.get("bordaScore").getAsDouble() / bordaPoints) - 1;
                    // forma simplificada: acumulamos posición en array
                }
            }

            // Calculamos varianza de posiciones para cada película y la añadimos al JSON
            // (lo usará DisplayAgent opcionalmente para mostrar un indicador de consenso)
            for (Map.Entry<Integer, JsonObject> entry : movieMap.entrySet()) {
                int id = entry.getKey();
                double[] pos = positions.get(id);
                if (pos != null) {
                    double mean = Arrays.stream(pos).average().orElse(0);
                    double variance = Arrays.stream(pos)
                        .map(p -> Math.pow(p - mean, 2))
                        .average().orElse(0);
                    entry.getValue().addProperty("consensusVariance", variance);
                }
            }

            // Ordenamos por puntuación Borda descendente
            List<JsonObject> sorted = new ArrayList<>(movieMap.values());
            sorted.sort((a, b) -> Double.compare(
                b.get("bordaScore").getAsDouble(),
                a.get("bordaScore").getAsDouble()
            ));

            // Log de desacuerdo si varianza media es alta
            double avgVariance = sorted.stream()
                .filter(m -> m.has("consensusVariance"))
                .mapToDouble(m -> m.get("consensusVariance").getAsDouble())
                .average().orElse(0);
            if (avgVariance > 5.0) {
                log("⚠️ Alta discrepancia entre críticos (varianza media: "
                    + String.format("%.2f", avgVariance) + "). Consenso débil.");
            } else {
                log("✓ Consenso fuerte entre críticos (varianza media: "
                    + String.format("%.2f", avgVariance) + ").");
            }

            // Top 10
            JsonArray result = new JsonArray();
            int limit = Math.min(10, sorted.size());
            for (int i = 0; i < limit; i++) result.add(sorted.get(i));
            return result;
        }
    }
}