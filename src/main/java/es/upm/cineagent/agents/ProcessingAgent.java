package es.upm.cineagent.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import es.upm.cineagent.launcher.AgentBase;
import es.upm.cineagent.launcher.AgentModel;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AgenteProcesamiento: recibe la lista de películas del AgentePercepcion,
 * aplica un algoritmo de puntuación multicriteria y envía el ranking
 * resultante al AgenteVisualizacion.
 *
 * Algoritmo de puntuación (cuando se especifican todos los criterios):
 *   puntuación = coincidencia_géneros * 0.40
 *              + valoración_TMDB_normalizada * 0.30
 *              + popularidad_normalizada * 0.15
 *              + coincidencia_época * 0.15
 */
public class ProcessingAgent extends AgentBase {

    public static final String NICKNAME = "ProcessingAgent";

    @Override
    protected void setup() {
        super.setup();
        this.type = AgentModel.PROCESSING;
        registerAgentDF();
        addBehaviour(new ProcessingBehaviour());
        log("AgenteProcesamiento iniciado y registrado en el DF.");
    }

    // -----------------------------------------------------------------------
    // COMPORTAMIENTO PRINCIPAL
    // -----------------------------------------------------------------------

    /**
     * Comportamiento cíclico que espera (modo bloqueante) la lista de películas
     * del AgentePercepcion, las puntúa y ordena, y envía el top 10 al
     * AgenteVisualizacion.
     */
    private class ProcessingBehaviour extends CyclicBehaviour {

        @Override
        public void action() {

            // Filtro bloqueante: esperamos REQUEST con ontología "movie-process"
            ACLMessage msg = blockingReceive(
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchOntology("movie-process")
                )
            );

            if (msg != null) {
                log("Lista de películas recibida para procesar.");

                JsonObject payload   = JsonParser.parseString(msg.getContent()).getAsJsonObject();
                JsonObject prefs     = payload.getAsJsonObject("preferences");
                JsonArray  movies    = payload.getAsJsonArray("movies");

                // Puntuamos y ordenamos las películas
                JsonArray rankedMovies = scoreAndRank(movies, prefs);
                log("Películas puntuadas y ordenadas. Top: " + rankedMovies.size());

                // Buscamos el DisplayAgent en el DF
                DFAgentDescription[] agents = getAgentsDF(AgentModel.DISPLAY);

                if (agents.length > 0) {
                    ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                    inform.addReceiver(agents[0].getName());
                    inform.setContent(rankedMovies.toString());
                    inform.setOntology("movie-results");
                    send(inform);
                    log("Ranking enviado al DisplayAgent.");
                } else {
                    loge("No se encontró DisplayAgent en el DF.");
                }
            }
        }

        /**
         * Aplica el algoritmo de puntuación multicriteria a cada película
         * y devuelve el top 10 ordenado de mayor a menor puntuación.
         *
         * @param movies  Array JSON de películas devueltas por TMDB
         * @param prefs   Preferencias del usuario (géneros, época, etc.)
         * @return        JsonArray con las 10 mejores películas puntuadas
         */
        private JsonArray scoreAndRank(JsonArray movies, JsonObject prefs) {

            String requestedGenres = prefs.get("genres").getAsString();
            String decadeStr       = prefs.get("decade").getAsString();

            // Conjunto de IDs de géneros solicitados (para comparación rápida)
            Set<String> genreSet = new HashSet<>();
            if (!requestedGenres.isEmpty()) {
                genreSet.addAll(Arrays.asList(requestedGenres.split(",")));
            }

            // Buscamos la popularidad máxima para normalizar (escala 0-1)
            double maxPopularity = 1.0;
            for (JsonElement el : movies) {
                JsonObject m = el.getAsJsonObject();
                if (m.has("popularity")) {
                    double pop = m.get("popularity").getAsDouble();
                    if (pop > maxPopularity) maxPopularity = pop;
                }
            }

            List<JsonObject> movieList = new ArrayList<>();

            for (JsonElement el : movies) {
                JsonObject m = el.getAsJsonObject().deepCopy();

                // --- 1. Puntuación por coincidencia de géneros (0 a 1) ---
                double genreScore = 1.0; // Si no se pidieron géneros, no penalizamos
                if (!genreSet.isEmpty() && m.has("genre_ids")) {
                    JsonArray movieGenres = m.getAsJsonArray("genre_ids");
                    int matches = 0;
                    for (JsonElement g : movieGenres) {
                        if (genreSet.contains(String.valueOf(g.getAsInt()))) {
                            matches++;
                        }
                    }
                    genreScore = (double) matches / genreSet.size();
                }

                // --- 2. Puntuación por valoración TMDB (0 a 1) ---
                double ratingScore = 0.5;
                if (m.has("vote_average") && !m.get("vote_average").isJsonNull()) {
                    ratingScore = m.get("vote_average").getAsDouble() / 10.0;
                }

                // --- 3. Puntuación por popularidad normalizada (0 a 1) ---
                double popularityScore = 0.5;
                if (m.has("popularity") && !m.get("popularity").isJsonNull()) {
                    popularityScore = m.get("popularity").getAsDouble() / maxPopularity;
                }

                // --- 4. Puntuación por época (0 a 1) ---
                double decadeScore = 0.5;
                if (!decadeStr.isEmpty() && m.has("release_date")
                        && !m.get("release_date").isJsonNull()) {
                    String releaseDate = m.get("release_date").getAsString();
                    if (releaseDate.length() >= 4) {
                        try {
                            int movieYear  = Integer.parseInt(releaseDate.substring(0, 4));
                            int decadeYear = Integer.parseInt(decadeStr);
                            if (movieYear >= decadeYear && movieYear < decadeYear + 10) {
                                decadeScore = 1.0; // Pertenece exactamente a la época
                            } else {
                                // Penalización proporcional a la distancia
                                int diff = Math.abs(movieYear - (decadeYear + 5));
                                decadeScore = Math.max(0, 1.0 - diff / 30.0);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                // --- Puntuación final ponderada ---
                double finalScore;
                boolean hasGenres = !genreSet.isEmpty();
                boolean hasDecade = !decadeStr.isEmpty();

                if (hasGenres && hasDecade) {
                    finalScore = genreScore * 0.40 + ratingScore * 0.30
                               + popularityScore * 0.15 + decadeScore * 0.15;
                } else if (hasGenres) {
                    finalScore = genreScore * 0.45 + ratingScore * 0.35 + popularityScore * 0.20;
                } else if (hasDecade) {
                    finalScore = ratingScore * 0.45 + popularityScore * 0.25 + decadeScore * 0.30;
                } else {
                    finalScore = ratingScore * 0.55 + popularityScore * 0.45;
                }

                // Redondeamos a 2 decimales y añadimos al JSON de la película
                m.addProperty("cineAgentScore", Math.round(finalScore * 100.0) / 100.0);
                movieList.add(m);
            }

            // Ordenamos de mayor a menor puntuación
            movieList.sort((a, b) -> Double.compare(
                b.get("cineAgentScore").getAsDouble(),
                a.get("cineAgentScore").getAsDouble()
            ));

            // Devolvemos el top 10
            JsonArray result = new JsonArray();
            int limit = Math.min(10, movieList.size());
            for (int i = 0; i < limit; i++) {
                result.add(movieList.get(i));
            }
            return result;
        }
    }
}
