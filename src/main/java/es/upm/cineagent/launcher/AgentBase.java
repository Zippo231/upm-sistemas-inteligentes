package es.upm.cineagent.launcher;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.Arrays;

public abstract class AgentBase extends Agent {

    protected AgentModel type;

   
    protected String[] params;

    @Override
    protected void setup() {
        super.setup();
        if (getArguments() != null && getArguments().length > 0) {
            this.params = Arrays.stream(getArguments())
                    .map(Object::toString)
                    .toArray(String[]::new);
        }
    }

   
    public DFAgentDescription[] getAgentsDF(AgentModel type) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription templateSd = new ServiceDescription();
        templateSd.setType(type.getValue());
        template.addServices(templateSd);
        DFAgentDescription[] result = new DFAgentDescription[0];
        try {
            jade.domain.FIPAAgentManagement.SearchConstraints sc = 
                new jade.domain.FIPAAgentManagement.SearchConstraints();
            sc.setMaxResults((long) 10);
            result = DFService.search(this, template, sc);
        } catch (FIPAException e) {
            loge("Error al buscar en el DF: " + e.getMessage());
        }
        return result;
    }

    /**
     * Registra este agente en el Directory Facilitator con su tipo de servicio.
     */
    public void registerAgentDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(this.getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(this.type.getValue());
        sd.setName(this.getLocalName());
        dfd.addServices(sd);
        try {
            DFAgentDescription[] results = DFService.search(this, dfd);
            if (results == null || results.length == 0) {
                DFService.register(this, dfd);
                log("Registrado en el DF como: " + this.type.getValue());
            }
        } catch (FIPAException e) {
            loge("No se pudo registrar en el DF.");
            this.doDelete();
        }
    }

    
    public void deregisterAgentDF() {
        try {
            DFService.deregister(this);
            log("Desregistrado del DF.");
        } catch (FIPAException e) {
            loge("No se pudo desregistrar del DF.");
        }
    }

    @Override
    public void doDelete() {
        deregisterAgentDF();
        super.doDelete();
        loge("Agente eliminado.");
    }

    public void log(String s) {
        System.out.println(System.currentTimeMillis() + ": "
                + getLocalName() + "(" + getClass().getSimpleName() + ") " + s);
    }

    public void loge(String s) {
        System.err.println(System.currentTimeMillis() + ": "
                + getLocalName() + "(" + getClass().getSimpleName() + ") " + s);
    }
}
