package es.upm.cineagent.launcher;

import es.upm.cineagent.agents.CriticAgent;
import es.upm.cineagent.agents.DisplayAgent;
import es.upm.cineagent.agents.JuryAgent;
import es.upm.cineagent.agents.SearchAgent;
import es.upm.cineagent.agents.UserAgent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.StaleProxyException;

public class Main {

    private static jade.wrapper.AgentContainer cc;

    private static void loadBoot() {
        jade.core.Runtime rt = jade.core.Runtime.instance();
        rt.setCloseVM(true);
        System.out.println("Runtime creado");

        Profile profile = new ProfileImpl(null, 1200, null);
        System.out.println("Perfil creado");

        System.out.println("Iniciando plataforma JADE...");
        cc = rt.createMainContainer(profile);

        try {
            ProfileImpl pContainer = new ProfileImpl(null, 1200, null);
            rt.createAgentContainer(pContainer);
            System.out.println("Contenedores creados");

            cc.createNewAgent("rma", "jade.tools.rma.rma", new Object[0]).start();

            // Receptores primero
            cc.createNewAgent(DisplayAgent.NICKNAME,
                    DisplayAgent.class.getName(), new Object[]{"0"}).start();

            cc.createNewAgent(JuryAgent.NICKNAME,
                    JuryAgent.class.getName(), new Object[]{"0"}).start();

            cc.createNewAgent(CriticAgent.NICKNAME_RATING,
                    CriticAgent.class.getName(), new Object[]{"RATING"}).start();

            cc.createNewAgent(CriticAgent.NICKNAME_POPULARITY,
                    CriticAgent.class.getName(), new Object[]{"POPULARITY"}).start();

            cc.createNewAgent(CriticAgent.NICKNAME_GENRE,
                    CriticAgent.class.getName(), new Object[]{"GENRE_MATCH"}).start();

            // SearchAgent después
            cc.createNewAgent(SearchAgent.NICKNAME,
                    SearchAgent.class.getName(), new Object[]{"0"}).start();

            // Esperamos registro en el DF
            Thread.sleep(1500);

            // UserAgent el último
            cc.createNewAgent(UserAgent.NICKNAME,
                    UserAgent.class.getName(), new Object[]{"0"}).start();

            System.out.println("Sistema CineAgent iniciado correctamente.");

        } catch (StaleProxyException e) {
            System.err.println("Error al crear agentes: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        System.out.println("Iniciando CineAgent...");
        loadBoot();
        System.out.println("Sistema MAS cargado.");
    }
}