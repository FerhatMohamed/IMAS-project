/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cat.urv.imas.agent;

import static cat.urv.imas.agent.ImasAgent.OWNER;
import cat.urv.imas.behaviour.central.RequestResponseBehaviour;
import cat.urv.imas.onthology.MessageContent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

/**
 *
 * @author Domen
 */
public class HospitalAgent extends ImasAgent{

    public HospitalAgent() {
        super(AgentType.HOSPITAL);
    }
    
    @Override
    protected void setup() {
        
        /* ** Very Important Line (VIL) ************************************* */
        this.setEnabledO2ACommunication(true, 1);

        // 1. Register the agent to the DF
        ServiceDescription sd1 = new ServiceDescription();
        sd1.setType(AgentType.HOSPITAL.toString());
        sd1.setName(getLocalName());
        sd1.setOwnership(OWNER);
        
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.addServices(sd1);
        dfd.setName(getAID());
        try {
            DFService.register(this, dfd);
            log("Registered to the DF");
        } catch (FIPAException e) {
            System.err.println(getLocalName() + " failed registration to DF [ko]. Reason: " + e.getMessage());
            doDelete();
        }
        
        
        //addBehaviour(new CyclicBehaviour(this)
        //{
        //    @Override
        //    public void action() {
        //        ACLMessage msg= receive();
        //                if (msg!=null){
        //                    System.out.println( " - " +myAgent.getLocalName() + " <- " + msg.getContent() );
        //                    
        //                    
        //                }
        //    }
        //    
        //}
        //);
        
        
    }
}


