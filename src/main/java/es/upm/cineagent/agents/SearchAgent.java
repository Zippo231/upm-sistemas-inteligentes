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

public class SearchAgent extends AgentBase {

    public static final String NICKNAME = "SearchAgent";
    // add the config.properties file in the main folder. 
    private String getApiKey() {
        try {
            java.util.Properties prop = new java.util.Properties();
            prop.load(new java.io.FileInputStream("config.properties"));
            return prop.getProperty("TMDB_API_KEY");
        } catch (Exception e) {
            System.err.println("File config.properties not found. Bro check the whatsapp group");
            return "KEY_NOT_FOUND";
        }
    }

    // - OPTION 2 - uncomment next code line - and fix the "queryTMDB"
    // !-!-!-! THE API KEY IS IN THE WHATSAPP GROUP !-!-!-!
    //private static final String TMDB_API_KEY = "PUT YOUR KEY HERE";
    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";

  

    @Override
    protected void setup() {
        super.setup();
        this.type = AgentModel.SEARCH;
        registerAgentDF();
        addBehaviour(new SearchBehaviour());
        log("AgentePercepcion iniciado y registrado en el DF.");
    }

    private class SearchBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = blockingReceive(
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchOntology("movie-search")
                )
            );

            if (msg != null) {
                log("Preferencias recibidas: " + msg.getContent());

                try {
                    String tmdbResponse = queryTMDB(msg.getContent());
                    log("Respuesta TMDB: " + tmdbResponse);

                    int startIndex = tmdbResponse.indexOf('{');
                    if (startIndex > 0) tmdbResponse = tmdbResponse.substring(startIndex);

                    JsonObject tmdbJson = JsonParser.parseString(tmdbResponse).getAsJsonObject();
                    JsonArray movies = tmdbJson.getAsJsonArray("results");

                    if (movies == null || movies.size() == 0) {
                        log("TMDB no devolvió resultados para esas preferencias.");
                        movies = new JsonArray();
                    }

                    log("TMDB devolvió " + movies.size() + " películas.");

                    // Construimos el payload una sola vez
                    JsonObject payload = new JsonObject();
                    payload.add("preferences", JsonParser.parseString(msg.getContent()));
                    payload.add("movies", movies);
                    String payloadStr = payload.toString();

                    // Buscamos los CriticAgents en el DF
                    DFAgentDescription[] critics = getAgentsDF(AgentModel.CRITIC);

                    if (critics.length > 0) {
                        ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
                        for (DFAgentDescription critic : critics) {
                            propose.addReceiver(critic.getName());
                        }
                        propose.setContent(payloadStr);
                        propose.setOntology("movie-evaluate");
                        send(propose);
                        log("Películas propuestas a " + critics.length + " críticos en paralelo.");
                    } else {
                        loge("No se encontraron CriticAgents en el DF.");
                    }

                } catch (Exception e) {
                    loge("Error al consultar TMDB: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private String queryTMDB(String prefsJson) throws Exception {
            JsonObject prefs = JsonParser.parseString(prefsJson).getAsJsonObject();

            String genres    = prefs.get("genres").getAsString();
            String minRating = prefs.get("minRating").getAsString();
            String decade    = prefs.get("decade").getAsString();
            String language  = prefs.get("language").getAsString();

            StringBuilder url = new StringBuilder(TMDB_BASE_URL + "/discover/movie");
            url.append("?api_key=").append(TMDB_API_KEY);
            url.append("&language=es-ES");
            url.append("&vote_count.gte=80");
            url.append("&sort_by=popularity.desc");
            url.append("&page=1");

            if (!genres.isEmpty()) {
                url.append("&with_genres=").append(genres);
            }

            if (!minRating.isEmpty()) {
                double rating = Double.parseDouble(minRating.replace(",", "."));
                if (rating > 0) {
                    url.append("&vote_average.gte=").append(
                        String.format(java.util.Locale.US, "%.1f", rating));
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