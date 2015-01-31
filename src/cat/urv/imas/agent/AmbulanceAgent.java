/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cat.urv.imas.agent;

import cat.urv.imas.map.Cell;
import cat.urv.imas.map.StreetCell;
import cat.urv.imas.onthology.GameSettings;
import cat.urv.imas.onthology.MessageContent;
import cat.urv.imas.utils.MessageType;
import cat.urv.imas.utils.Utils;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;
import org.newdawn.slick.util.pathfinding.Path;


/**
 *
 * @author Mohammed
 */
public class AmbulanceAgent extends NavigatorAgent {

    public AmbulanceAgent() {
        super(AgentType.AMBULANCE);
    }
    
    /**
     * Game settings in use.
     */
    private GameSettings game;
    
    /**
     * Fireman-Coordinator agent id.
     */
    private AID hospitalCoordinatorAgent;

    private int mutualMove = 1;    

    @Override
    protected void setup() {
        super.setup(); //To change body of generated methods, choose Tools | Templates.
        
        this.registerService(AgentType.AMBULANCE.toString());
        
        ServiceDescription searchCriterion = new ServiceDescription();
        //searchCriterion.setType(AgentType.COORDINATOR.toString());
        //this.coordinatorAgent = UtilsAgents.searchAgent(this, searchCriterion);
        
        searchCriterion.setType(AgentType.HOSPITAL_COORDINATOR.toString());
        this.hospitalCoordinatorAgent = UtilsAgents.searchAgent(this, searchCriterion);
        
        ACLMessage notifyFiremenCoordinator = new ACLMessage( ACLMessage.SUBSCRIBE );
        notifyFiremenCoordinator.addReceiver(this.hospitalCoordinatorAgent);
        this.send(notifyFiremenCoordinator);
        this.log("subscription request sent.");        
        
        this.addBehaviour(new CyclicBehaviour() {
            
            private AmbulanceAgent aa = AmbulanceAgent.this;
            private MessageTemplate mt = MessageTemplate.and(
                        MessageTemplate.MatchSender(aa.hospitalCoordinatorAgent), 
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM));            
            @Override
            public void action() {
                ACLMessage msg = null;
                
                while((msg = aa.receive(mt)) != null) {
                    try {
                        MessageContent mc = (MessageContent) msg.getContentObject();
                        if(mc.getMessageType() == MessageType.INFORM_CITY_STATUS) {
                            aa.game = (GameSettings) mc.getContent();
                            aa.updateAgentPosition();

                            
                            // Get Next position
                            Cell cPosition = aa.agentPosition;
                            int[] nextPosition = new int[2];
                            nextPosition[0] = cPosition.getRow();
                            nextPosition[1] = cPosition.getCol() + mutualMove;

                            Cell[][] map = aa.game.getMap();
                            if (!(map[nextPosition[0]][nextPosition[1]] instanceof StreetCell)) {
                                nextPosition[1] = cPosition.getCol() - mutualMove;
                            }

                            mutualMove *= -1;

                            Object[] actionData = new Object[]{aa.getAID().getLocalName(), nextPosition};
                            ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                            reply.addReceiver(aa.hospitalCoordinatorAgent);
                            reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                            aa.log("Inform message to agent");
                            try {
                                reply.setContentObject(new MessageContent(MessageType.TURN_IS_DONE, actionData));
                            } 
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                            aa.send(reply);
                            aa.log("Inform message content: " + MessageType.TURN_IS_DONE.toString());
                        }
                    }
                    catch(Exception ex) {
                        
                    }
                }
                block();
            }
        });
        
                
        this.addBehaviour(new AuctionResponser(this, MessageTemplate.and(MessageTemplate.MatchSender(this.hospitalCoordinatorAgent), MessageTemplate.MatchPerformative(ACLMessage.CFP))));
        
    }
    
    private class AuctionResponser extends ContractNetResponder {

        public AuctionResponser(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
            AmbulanceAgent aa = (AmbulanceAgent) this.myAgent;
            ACLMessage reply = cfp.createReply();
            reply.setPerformative(ACLMessage.PROPOSE);
            try {
                MessageContent mc = (MessageContent) cfp.getContentObject();
                Cell cell = (Cell) mc.getContent();
                Path p = Utils.getShortestPath(aa.game.getMap(), aa.agentPosition, aa.findFreeCell(cell));
                
                //steps.add((int)findShortestPath((Cell)bc));
                if(p != null) {
                    reply.setContent(p.getLength() + "");
                }
                else {
                    //Thats mean the agent can not go to this position, we can add negative value to refer to this 
                    reply.setContent("-1");
                }                
            }
            catch(Exception ex) {
                
            }
            
            return reply;
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
            return super.handleAcceptProposal(cfp, propose, accept); //To change body of generated methods, choose Tools | Templates.
        }
        
        
        
    }
    
    private void updateAgentPosition() {
        int fIdx = Integer.valueOf(this.getLocalName().substring(this.getLocalName().length() - 1)) - 1;
        this.agentPosition = this.game.getAgentList().get(AgentType.AMBULANCE).get(fIdx);
        log("Position updated: " + this.agentPosition.getRow() + "," + this.agentPosition.getCol() + "");        
    }    
}
