package es.upm.cineagent.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import es.upm.cineagent.launcher.AgentBase;
import es.upm.cineagent.launcher.AgentModel;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * AgentePercepcion: recibe las preferencias del AgenteUsuario,
 * consulta la API de TMDB y envía la lista de películas encontradas
 * al AgenteProcesamiento.
 *
 * IMPORTANTE: sustituye TU_API_KEY_AQUI por tu clave de TMDB.
 * Puedes obtenerla gratis en: https://www.themoviedb.org/settings/api
 */
public class SearchAgent extends AgentBase {

    public static final String NICKNAME = "SearchAgent";

    // *** SUSTITUIR POR TU CLAVE DE API DE TMDB ***
    // !-!-!-! THE API KEY IS IN THE WHATSAPP GROUP DESCRIPTION !-!-!-!
    private static final String TMDB_API_KEY = "PUT YOUR KEY HERE";
    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";

    @Override
    protected void setup() {
        super.setup();
        this.type = AgentModel.SEARCH;
        registerAgentDF();
        addBehaviour(new SearchBehaviour());
        log("AgentePercepcion iniciado y registrado en el DF.");
    }

    // -----------------------------------------------------------------------
    // COMPORTAMIENTO PRINCIPAL
    // -----------------------------------------------------------------------

    /**
     * Comportamiento cíclico que espera (modo bloqueante) una petición
     * de búsqueda del AgenteUsuario, consulta TMDB y reenvía el resultado
     * al AgenteProcesamiento.
     */
    private class SearchBehaviour extends CyclicBehaviour {

        @Override
        public void action() {

            // Filtro bloqueante: esperamos un REQUEST con ontología "movie-search"
            ACLMessage msg = blockingReceive(
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchOntology("movie-search")
                )
            );

            if (msg != null) {
                log("Preferencias recibidas: " + msg.getContent());

                try {
                    // Consultamos la API de TMDB
                	String tmdbResponse = queryTMDB(msg.getContent());
                	log("Respuesta TMDB: " + tmdbResponse);
                	// Limpiamos caracteres extraños al inicio del JSON
                	int startIndex = tmdbResponse.indexOf('{');
                	if (startIndex > 0) tmdbResponse = tmdbResponse.substring(startIndex);
                	JsonObject tmdbJson = JsonParser.parseString(tmdbResponse).getAsJsonObject();
                    JsonArray movies = tmdbJson.getAsJsonArray("results");

                    if (movies == null || movies.size() == 0) {
                        log("TMDB no devolvió resultados para esas preferencias.");
                        movies = new JsonArray();
                    }

                    log("TMDB devolvió " + movies.size() + " películas.");

                    // Construimos el payload que enviaremos al ProcessingAgent:
                    // incluye tanto las preferencias como las películas encontradas
                    JsonObject payload = new JsonObject();
                    payload.add("preferences", JsonParser.parseString(msg.getContent()));
                    payload.add("movies", movies);

                    // Buscamos el ProcessingAgent en el DF
                    DFAgentDescription[] agents = getAgentsDF(AgentModel.PROCESSING);

                    if (agents.length > 0) {
                        ACLMessage forward = new ACLMessage(ACLMessage.REQUEST);
                        forward.addReceiver(agents[0].getName());
                        forward.setContent(payload.toString());
                        forward.setOntology("movie-process");
                        send(forward);
                        log("Lista de películas enviada al ProcessingAgent.");
                    } else {
                        loge("No se encontró ProcessingAgent en el DF.");
                    }

                } catch (Exception e) {
                    loge("Error al consultar TMDB: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        /**
         * Construye la URL de la API de TMDB y realiza la llamada HTTP.
         * @param prefsJson JSON con las preferencias del usuario
         * @return Respuesta JSON de TMDB como String
         */
        private String queryTMDB(String prefsJson) throws Exception {
            JsonObject prefs = JsonParser.parseString(prefsJson).getAsJsonObject();

            String genres   = prefs.get("genres").getAsString();
            String minRating = prefs.get("minRating").getAsString();
            String decade   = prefs.get("decade").getAsString();
            String language = prefs.get("language").getAsString();

            // Construimos la URL con los filtros seleccionados
            StringBuilder url = new StringBuilder(TMDB_BASE_URL + "/discover/movie");
            url.append("?api_key=").append(TMDB_API_KEY);
            url.append("&language=es-ES");          // Respuesta en español
            url.append("&vote_count.gte=80");       // Mínimo de votos para fiabilidad
            url.append("&sort_by=popularity.desc"); // Ordenar por popularidad
            url.append("&page=1");

            if (!genres.isEmpty()) {
                url.append("&with_genres=").append(genres);
            }

            if (!minRating.isEmpty()) {
            	double rating = Double.parseDouble(minRating.replace(",", "."));
                if (rating > 0) {
                	url.append("&vote_average.gte=").append(String.format(java.util.Locale.US, "%.1f", rating));
                }
            }

            if (!decade.isEmpty()) {
                int year = Integer.parseInt(decade);
                url.append("&primary_release_date.gte=").append(year).append("-01-01");
                url.append("&primary_release_date.lte=").append(year + 9).append("-12-31");
            }

            if (!language.isEmpty()) {
                url.append("&with_original_language=").append(language);
            }

            log("Consultando TMDB: " + url);
            return httpGet(url.toString());
        }

        /**
         * Realiza una petición HTTP GET y devuelve la respuesta como String.
         */
        private String httpGet(String urlString) throws Exception {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                .url(urlString)
                .header("Accept", "application/json")
                .build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                log("HTTP status: " + response.code());
                String result = response.body().string();
                log("Respuesta TMDB (" + result.length() + " chars): "
                    + result.substring(0, Math.min(150, result.length())));
                return result;
            }
        }
    }
}
