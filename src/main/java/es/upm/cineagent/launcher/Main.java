package es.upm.cineagent.launcher;

import es.upm.cineagent.agents.DisplayAgent;
import es.upm.cineagent.agents.ProcessingAgent;
import es.upm.cineagent.agents.SearchAgent;
import es.upm.cineagent.agents.UserAgent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.StaleProxyException;

/**
 * Clase principal que lanza la plataforma JADE y crea todos los agentes
 * del sistema CineAgent.
 *
 * Ejecución: Run as Java Application sobre esta clase.
 */
public class Main {

    private static jade.wrapper.AgentContainer cc;

    private static void loadBoot() {
        // Creamos la instancia de entorno de ejecución JADE
        jade.core.Runtime rt = jade.core.Runtime.instance();
        rt.setCloseVM(true);
        System.out.println("Runtime creado");

        // Configuración del contenedor principal
        Profile profile = new ProfileImpl(null, 1200, null);
        System.out.println("Perfil creado");

        // Creamos el contenedor principal de JADE
        System.out.println("Iniciando plataforma JADE...");
        cc = rt.createMainContainer(profile);

        try {
            // Contenedor adicional
            ProfileImpl pContainer = new ProfileImpl(null, 1200, null);
            rt.createAgentContainer(pContainer);
            System.out.println("Contenedores creados");

            // Agente RMA (interfaz de administración de JADE)
            cc.createNewAgent("rma", "jade.tools.rma.rma", new Object[0]).start();

            // Arrancamos los agentes en orden:
            // Primero Display, Processing y Search (para que estén registrados en el DF)
            cc.createNewAgent(DisplayAgent.NICKNAME,
                    DisplayAgent.class.getName(), new Object[]{"0"}).start();

            cc.createNewAgent(ProcessingAgent.NICKNAME,
                    ProcessingAgent.class.getName(), new Object[]{"0"}).start();

            cc.createNewAgent(SearchAgent.NICKNAME,
                    SearchAgent.class.getName(), new Object[]{"0"}).start();

            // Esperamos a que los agentes anteriores se registren en el DF
            Thread.sleep(1500);

            // Último en arrancar: UserAgent (muestra la interfaz al usuario)
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
