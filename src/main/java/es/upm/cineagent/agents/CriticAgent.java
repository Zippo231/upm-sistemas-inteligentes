package es.upm.cineagent.agents;

import com.google.gson.*;
import es.upm.cineagent.launcher.AgentBase;
import es.upm.cineagent.launcher.AgentModel;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;


public class CriticAgent extends AgentBase {

    public enum Role { RATING, POPULARITY, GENRE_MATCH }

    public static final String NICKNAME_RATING    = "CriticRating";
    public static final String NICKNAME_POPULARITY = "CriticPopularity";
    public static final String NICKNAME_GENRE      = "CriticGenre";

    private Role role;

    @Override
    protected void setup() {
        super.setup();

      
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            role = Role.valueOf(args[0].toString());
        } else {
            role = Role.RATING; // fallback
        }

        this.type = AgentModel.CRITIC; 
        registerAgentDF();
        addBehaviour(new CriticBehaviour());
        log("CriticAgent [" + role + "] iniciado.");
    }

    private class CriticBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = blockingReceive(
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                    MessageTemplate.MatchOntology("movie-evaluate")
                )
            );

            if (msg != null) {
                log("[" + role + "] Evaluando películas...");

                JsonObject payload = JsonParser.parseString(msg.getContent()).getAsJsonObject();
                JsonArray  movies  = payload.getAsJsonArray("movies");
                JsonObject prefs   = payload.getAsJsonObject("preferences");

                JsonArray ranked = evaluate(movies, prefs);


                DFAgentDescription[] jury = getAgentsDF(AgentModel.JURY);
                if (jury.length > 0) {
                    JsonObject vote = new JsonObject();
                    vote.addProperty("role", role.name());
                    vote.add("ranking", ranked);

                    ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                    inform.addReceiver(jury[0].getName());
                    inform.setContent(vote.toString());
                    inform.setOntology("critic-vote");
                    send(inform);
                    log("[" + role + "] Voto enviado al JuryAgent.");
                } else {
                    loge("No se encontró JuryAgent en el DF.");
                }
            }
        }

        private JsonArray evaluate(JsonArray movies, JsonObject prefs) {
            List<JsonObject> list = new ArrayList<>();

            double maxPop = 1.0;
            for (JsonElement el : movies) {
                double p = el.getAsJsonObject().has("popularity")
                    ? el.getAsJsonObject().get("popularity").getAsDouble() : 0;
                if (p > maxPop) maxPop = p;
            }

            Set<String> genreSet = new HashSet<>();
            String genresStr = prefs.has("genres") ? prefs.get("genres").getAsString() : "";
            if (!genresStr.isEmpty()) {
                for (String g : genresStr.split(",")) genreSet.add(g.trim());
            }

            for (JsonElement el : movies) {
                JsonObject m = el.getAsJsonObject().deepCopy();
                double score;

                switch (role) {
                    case RATING:
                        score = m.has("vote_average")
                            ? m.get("vote_average").getAsDouble() / 10.0 : 0.5;
                        break;

                    case POPULARITY:
                        score = m.has("popularity")
                            ? m.get("popularity").getAsDouble() / maxPop : 0.5;
                        break;

                    case GENRE_MATCH:
                    default:
                      
                        if (genreSet.isEmpty()) {
                            score = m.has("vote_average")
                                ? m.get("vote_average").getAsDouble() / 10.0 : 0.5;
                        } else {
                            int matches = 0;
                            if (m.has("genre_ids")) {
                                for (JsonElement g : m.getAsJsonArray("genre_ids")) {
                                    if (genreSet.contains(String.valueOf(g.getAsInt()))) matches++;
                                }
                            }
                            score = (double) matches / genreSet.size();
                        }
                        break;
                }

                m.addProperty("criticScore", Math.round(score * 1000.0) / 1000.0);
                list.add(m);
            }

            list.sort((a, b) -> Double.compare(
                b.get("criticScore").getAsDouble(),
                a.get("criticScore").getAsDouble()
            ));

            JsonArray result = new JsonArray();
            list.forEach(result::add);
            return result;
        }
    }
}