/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cat.urv.imas.agent;

import cat.urv.imas.behaviour.firemenCoordinator.RequestResponseBehaviour;
import cat.urv.imas.onthology.GameSettings;
import java.util.Iterator;
import cat.urv.imas.behaviour.coordinator.RequesterBehaviour;
import cat.urv.imas.map.BuildingCell;
import cat.urv.imas.map.Cell;
import cat.urv.imas.onthology.MessageContent;
import cat.urv.imas.utils.MessageList;
import cat.urv.imas.utils.MessageType;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.Message1_1Impl;
import jade.core.*;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPANames.InteractionProtocol;
import jade.lang.acl.*;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Domen
 */
public class FiremenCoordinator extends ImasAgent{

    public static final String REQUEST_STATE = "REQUEST_STATUS";
    public static final String INITIAL_SEND_TO_FIRMEN = "INITIAL_SEND_TO_FIRMEN";
    public static final String RECEIVE_MOVMENTS = "RECEIVE_MOVMENTS";
    public static final String FORWARD_MOVMENTS = "FORWARD_MOVMENTS";
    public static final String SEND_NEW_INFO = "SEND_NEW_INFO";
    public static final String PERFORM_AUCTION = "PERFORM_AUCTION";
    public static final String RECEIVE_NEW_FIRES = "RECEIVE_NEW_FIRES";
    
    private MessageList messageList;
    
    private ArrayList<Cell> movementsList = new ArrayList<>();
    
    public Map<BuildingCell, Integer> newFires;
    
    /**
     * Game settings in use.
     */
    private GameSettings game;
    /**
     * Central agent id.
     */
    private AID coordinatorAgent;
    
    private int numberOfFiremen; 
    
    private Map<AID, List<Integer>> firemanResponses;  

    
    public FiremenCoordinator() {
        super(AgentType.FIREMEN_COORDINATOR);
        this.messageList = new MessageList(this);
    }
    
    private Map<BuildingCell, Integer> firesTakenCareOf;

    
    
    @Override
    protected void setup() {

        /* ** Very Important Line (VIL) ************************************* */
        this.setEnabledO2ACommunication(true, 1);

        // 1. Register the agent to the DF
        ServiceDescription sd1 = new ServiceDescription();
        sd1.setType(AgentType.FIREMEN_COORDINATOR.toString());
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
        
        firesTakenCareOf = new HashMap<>();
        
        // search CoordinatorAgent
        ServiceDescription searchCriterion = new ServiceDescription();
        searchCriterion.setType(AgentType.COORDINATOR.toString());
        this.coordinatorAgent = UtilsAgents.searchAgent(this, searchCriterion);

        // Finite State Machine
        FSMBehaviour fsm = new FSMBehaviour(this) {
            public int onEnd() {
                System.out.println("FSM behaviour completed.");
		myAgent.doDelete();
		return super.onEnd();
            }
	};
        
        fsm.registerFirstState(new RequestStateInfo(), FiremenCoordinator.REQUEST_STATE);
        fsm.registerState(new SendInitialState(), FiremenCoordinator.INITIAL_SEND_TO_FIRMEN);
        fsm.registerState(new ReceiveMovement(), FiremenCoordinator.RECEIVE_MOVMENTS);
        fsm.registerState(new SendMovement(), FiremenCoordinator.FORWARD_MOVMENTS);
        fsm.registerState(new SendNewInfo(), FiremenCoordinator.SEND_NEW_INFO);
        fsm.registerState(new ReceiveNewFires(), FiremenCoordinator.RECEIVE_NEW_FIRES);
        fsm.registerState(new PerformAuction(), FiremenCoordinator.PERFORM_AUCTION);
        
        
        fsm.registerTransition(FiremenCoordinator.REQUEST_STATE, FiremenCoordinator.INITIAL_SEND_TO_FIRMEN, 1);
        fsm.registerTransition(FiremenCoordinator.REQUEST_STATE, FiremenCoordinator.SEND_NEW_INFO, 2);
        
        fsm.registerDefaultTransition(FiremenCoordinator.INITIAL_SEND_TO_FIRMEN, FiremenCoordinator.RECEIVE_NEW_FIRES);
        fsm.registerDefaultTransition(FiremenCoordinator.SEND_NEW_INFO, FiremenCoordinator.RECEIVE_NEW_FIRES);
        fsm.registerDefaultTransition(FiremenCoordinator.INITIAL_SEND_TO_FIRMEN, FiremenCoordinator.RECEIVE_NEW_FIRES);
        fsm.registerDefaultTransition(FiremenCoordinator.RECEIVE_NEW_FIRES, FiremenCoordinator.PERFORM_AUCTION);
        
        fsm.registerDefaultTransition(FiremenCoordinator.PERFORM_AUCTION, FiremenCoordinator.REQUEST_STATE);
        
        
        //fsm.registerTransition(FiremenCoordinator.RECEIVE_MOVMENTS, FiremenCoordinator.RECEIVE_MOVMENTS, 1);
        fsm.registerTransition(FiremenCoordinator.RECEIVE_MOVMENTS, FiremenCoordinator.FORWARD_MOVMENTS, 1); //2
        //fsm.registerTransition(FiremenCoordinator.FORWARD_MOVMENTS, FiremenCoordinator.FORWARD_MOVMENTS, 1);
        fsm.registerTransition(FiremenCoordinator.FORWARD_MOVMENTS, FiremenCoordinator.REQUEST_STATE, 1); //2
        
        this.addBehaviour(fsm);
        
        /*        
        addBehaviour(new CyclicBehaviour(this)
        {
            @Override
            public void action() {
                ACLMessage msg = receive();
                        if (msg != null) {
                            System.out.println( " - " +
                               myAgent.getLocalName() + " <- " );
                              // msg.getContent() );
                            AID sender = msg.getSender();
                            
                            if(sender.equals(coordinatorAgent)) {
                                try {
                                    MessageContent mc = (MessageContent)msg.getContentObject();
                                    switch(mc.getMessageType()) {
                                        case INFORM_CITY_STATUS:

                                            GameSettings game = (GameSettings)mc.getContent();
                                            ACLMessage initialRequest = new ACLMessage(ACLMessage.INFORM);
                                            initialRequest.clearAllReceiver();
                                            ServiceDescription searchCriterion = new ServiceDescription();
                                            searchCriterion.setType(AgentType.FIREMAN.toString());  
                                            Map<AgentType, List<Cell>> a = game.getAgentList();
                                            List<Cell> FIR = a.get(AgentType.FIREMAN);
                                            setNumberOfFiremen(FIR.size());
                                            setGame(game); //we need to set game, so we can get fires (for now) 
                                            int i = 1;
                                            for (Cell FIR1 : FIR) {
                                                searchCriterion.setName("firemenAgent" + i);
                                                initialRequest.addReceiver(UtilsAgents.searchAgent(this.myAgent, searchCriterion));
                                                i++;
                                            }

                                           try {

                                               initialRequest.setContentObject(new MessageContent(MessageType.INFORM_CITY_STATUS, game));
                                              // log("Request message content:" + initialRequest.getContent());
                                           } catch (Exception e) {
                                               e.printStackTrace();
                                           }
                                           //newFires(); // don't forget to delete
                                           this.myAgent.send(initialRequest);                                        
                                            break;
                                        case NEW_FIRES:
                                            newFires(); //we will send location of new fire or fires
                                            break;
                                        default:
                                            this.block();
                                    }


                                   //this.send(initialRequest);

                                } catch (UnreadableException ex) {
                                    Logger.getLogger(HospitalCoordinator.class.getName()).log(Level.SEVERE, null, ex);
                                }

                                ((FiremenCoordinator)myAgent).informStepCoordinator();                                
                            }
                            if(msg.getPerformative()== ACLMessage.PROPOSE)
                            {
                                
                                MessageContent mc;
                                try {
                                     mc = (MessageContent)msg.getContentObject();
                                     firemanResponses.put(msg.getSender(), (List<Integer>)mc.getContent());
                                     
                                } catch (UnreadableException ex) {
                                    Logger.getLogger(FiremenCoordinator.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                if(firemanResponses.size()==numberOfFiremen)
                                {
                                    selectWinners();
                                }
                            }
                        }
                        else {
                            block();
                        }
            }

        }
        );*/        
        
        

        MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchProtocol(InteractionProtocol.FIPA_REQUEST), MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        //this.addBehaviour(new RequestResponseBehaviour(this, mt));

    }
    
    // FirmenCoordinator Behaviours
    
    protected class RequestStateInfo extends SimpleBehaviour {

        private FiremenCoordinator fc = FiremenCoordinator.this;
        private boolean isInitialRequest = true;
        
        @Override
        public void action() {
            fc.log(FiremenCoordinator.REQUEST_STATE);
            // Make the request
            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.clearAllReceiver();
            request.addReceiver(fc.coordinatorAgent);
            request.setProtocol(InteractionProtocol.FIPA_REQUEST);
            try {
		request.setContentObject(new MessageContent(MessageType.REQUEST_CITY_STATUS, null));
		fc.send(request);
		fc.log("Sending request state info to " + fc.coordinatorAgent.getLocalName());
            } catch (Exception e) {
		e.printStackTrace();
            }
            
            boolean isInfoOk = false;
            
            while(!isInfoOk) {
                ACLMessage reply = fc.messageList.getMessage();
                if(reply != null) {
                    switch(reply.getPerformative()) {
                        case ACLMessage.AGREE :
                            fc.log("Received AGREE from " + reply.getSender().getLocalName());
                            break;
                        case ACLMessage.INFORM:
                            try {
                                MessageContent mc = (MessageContent) reply.getContentObject();
                                if(mc.getMessageType() == MessageType.INFORM_CITY_STATUS) {
                                    isInfoOk = true;
                                    fc.log("Received Information from " + reply.getSender().getLocalName());
                                    fc.game = (GameSettings)mc.getContent();
                                    Map<AgentType, List<Cell>> a = fc.game.getAgentList();
                                    List<Cell> FIR = a.get(AgentType.FIREMAN);
                                    fc.setNumberOfFiremen(FIR.size());                                    
                                }
                            }
                            catch(Exception ex) {
                                ex.printStackTrace();
                                fc.messageList.addMessage(reply);
                            }
                            break;
                        case ACLMessage.FAILURE:
                            break;
                        default:
                            fc.messageList.addMessage(reply);
                    }
                }
            }
            fc.messageList.endRetrieval();
        }

        @Override
        public boolean done() {
            return true;
        }

        @Override
        public int onEnd() {
            fc.log(FiremenCoordinator.REQUEST_STATE + " DONE!");
            if(this.isInitialRequest) {
                this.isInitialRequest = false;
                return 1;
            }
            return 2;
        }
        
        
        
    }
    
    protected class SendInitialState extends SimpleBehaviour {

        private FiremenCoordinator fc = FiremenCoordinator.this;
        
        @Override
        public void action() {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.setProtocol(InteractionProtocol.FIPA_REQUEST);
            
            ServiceDescription searchCriterion = new ServiceDescription();
            searchCriterion.setType(AgentType.FIREMAN.toString()); 
            int idx[] = { 1 };
            fc.game.getAgentList().get(AgentType.FIREMAN).forEach(agentPosition -> {
                searchCriterion.setName("firemenAgent" + idx[0]++);
                msg.clearAllReceiver();
                AID receiver = UtilsAgents.searchAgent(fc, searchCriterion);
                msg.addReceiver(receiver);
                try {
                    msg.setContentObject(new MessageContent(MessageType.INFORM_CITY_STATUS, new Object[]{ agentPosition, fc.game }));
                } catch (IOException ex) {
                    msg.setPerformative(ACLMessage.FAILURE);
                    Logger.getLogger(FiremenCoordinator.class.getName()).log(Level.SEVERE, null, ex);
                }
                fc.send(msg);
                fc.log("Sending intial info to " + receiver);
            });
            

        }

        @Override
        public boolean done() {
            fc.log(FiremenCoordinator.INITIAL_SEND_TO_FIRMEN + " DONE!!");
            return true;
        }
        
    }

    protected class ReceiveMovement extends SimpleBehaviour {

        private FiremenCoordinator fc = FiremenCoordinator.this;
        private int movementsReceivedCount = 0;
        
        @Override
        public void action() {
            fc.log(FiremenCoordinator.RECEIVE_MOVMENTS);
            boolean isInfoReceived = false;
            while(!isInfoReceived) {
                ACLMessage msg = fc.messageList.getMessage();
                if(msg != null) {
                    switch(msg.getPerformative()) {
                        case ACLMessage.AGREE:
                            fc.log("Received AGREE from " + msg.getSender().getLocalName());
                            break;
                        case ACLMessage.INFORM:
                            try {
                                MessageContent mc = (MessageContent)msg.getContentObject();
                                if(mc.getMessageType() == MessageType.REQUEST_MOVE) {
                                    fc.log("New movement received from " + msg.getSender().getLocalName());
                                    isInfoReceived = true;
                                    movementsReceivedCount++;
                                    fc.movementsList.add((Cell)mc.getContent());
                                }
                                else {
                                    fc.messageList.addMessage(msg);
                                }
                            }
                            catch(Exception ex) {
                                ex.printStackTrace();
                            }
                            break;
                        case ACLMessage.FAILURE:
                            fc.log("Faild to receive new movement from " + msg.getSender().getLocalName());
                            break;
                        default:
                            fc.messageList.addMessage(msg);
                    }
                }
            }
            fc.messageList.endRetrieval();
        }

        @Override
        public boolean done() {
            return true;
        }

        @Override
        public int onEnd() {
            if(movementsReceivedCount < fc.game.getAgentList().get(AgentType.FIREMAN).size()) {
                return 1;
            }
            fc.log(FiremenCoordinator.RECEIVE_MOVMENTS + " DONE!!");
            movementsReceivedCount = 0;
            return 2;
        }
        
        
        
    }
    
    protected class SendMovement extends SimpleBehaviour {

        private FiremenCoordinator fc = FiremenCoordinator.this;
        
        @Override
        public void action() {
            
            fc.log(FiremenCoordinator.FORWARD_MOVMENTS);
            
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.clearAllReceiver();
            msg.addReceiver(fc.coordinatorAgent);
            msg.setProtocol(InteractionProtocol.FIPA_REQUEST);
            try {
                
                msg.setContentObject(new MessageContent(MessageType.REQUEST_MOVE, fc.movementsList.get(0)));
                fc.movementsList.remove(0);
                
            }
            catch(Exception ex) {
            }
            fc.send(msg);
        }

        @Override
        public boolean done() {
            return true;
        }

        @Override
        public int onEnd() {
            if(fc.movementsList.isEmpty()) {
                return 2;
            }
            fc.log(FiremenCoordinator.FORWARD_MOVMENTS + " DONE!!");
            return 1;
        }
        
        
        
    }
    
    protected class SendNewInfo extends SimpleBehaviour {

        private FiremenCoordinator fc = FiremenCoordinator.this;
        
        @Override
        public void action() {
            
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.setProtocol(InteractionProtocol.FIPA_REQUEST);
            
            ServiceDescription searchCriterion = new ServiceDescription();
            searchCriterion.setType(AgentType.FIREMAN.toString()); 
            int idx[] = { 1 };
            fc.game.getAgentList().get(AgentType.FIREMAN).forEach(agentPosition -> {
                searchCriterion.setName("firemenAgent" + idx[0]++);
                msg.clearAllReceiver();
                AID receiver = UtilsAgents.searchAgent(fc, searchCriterion);
                msg.addReceiver(receiver);
                try {
                    msg.setContentObject(new MessageContent(MessageType.INFORM_CITY_STATUS, new Object[]{ null, fc.game }));
                } catch (IOException ex) {
                    msg.setPerformative(ACLMessage.FAILURE);
                    Logger.getLogger(FiremenCoordinator.class.getName()).log(Level.SEVERE, null, ex);
                }
                fc.send(msg);
                fc.log("Sending intial info to " + receiver);
            });            
        }

        @Override
        public boolean done() {
            fc.log(FiremenCoordinator.SEND_NEW_INFO + " DONE!!");
            return true;
        }
        
    }
    
    protected class PerformAuction extends SimpleBehaviour {

        private FiremenCoordinator fc = FiremenCoordinator.this;
        
        @Override
        public void action() {
            fc.log("Start Auction");
            
            ACLMessage msg = new ACLMessage(ACLMessage.CFP);
            msg.setProtocol(InteractionProtocol.FIPA_REQUEST);
            
            ServiceDescription searchCriterion = new ServiceDescription();
            searchCriterion.setType(AgentType.FIREMAN.toString()); 
            int idx[] = { 1 };
            fc.game.getAgentList().get(AgentType.FIREMAN).forEach(agentPosition -> {
                searchCriterion.setName("firemenAgent" + idx[0]++);
                msg.clearAllReceiver();
                AID receiver = UtilsAgents.searchAgent(fc, searchCriterion);
                msg.addReceiver(receiver);
                try {
                    msg.setContentObject(new MessageContent(MessageType.NEW_FIRES, newFires));
                } catch (IOException ex) {
                    msg.setPerformative(ACLMessage.FAILURE);
                    Logger.getLogger(FiremenCoordinator.class.getName()).log(Level.SEVERE, null, ex);
                }                            
                fc.send(msg);
            });    
            
            
            
            
            fc.firemanResponses = new HashMap<>();
            
            int bidsCount = 0;
            
            //fc.log("FIREMEN : " + fc.numberOfFiremen);
            
            while(bidsCount <  fc.numberOfFiremen) {
                
                boolean isBidReceived = false;
                while (!isBidReceived) {
                    ACLMessage bidMsg = fc.messageList.getMessage();
                    if(bidMsg != null) {
                        switch(bidMsg.getPerformative()) {
                            case ACLMessage.PROPOSE:
                                try {
                                    MessageContent mc = (MessageContent)bidMsg.getContentObject();
                                    if(mc != null) {
                                        fc.firemanResponses.put(bidMsg.getSender(), (List<Integer>)mc.getContent());
                                        fc.log(fc.firemanResponses.size() + " Responses ");
                                        bidsCount++;
                                        isBidReceived = true;
                                    }
                                    else {
                                        fc.messageList.addMessage(bidMsg);
                                    }
                                }
                                catch(Exception ex) {
                                    ex.printStackTrace();
                                    fc.messageList.addMessage(bidMsg);
                                }
                                break;
                            case ACLMessage.FAILURE:
                                fc.log("FAIELD to receive bid from " + bidMsg.getSender().getLocalName());
                                break;
                            default:
                                fc.messageList.addMessage(bidMsg);
                        }
                    }
                }
                
            }
            
            fc.messageList.endRetrieval();
            
            
            fc.selectWinners();
            
        }

        @Override
        public boolean done() {
            return true;
        }
        
    }
    
    protected class ReceiveNewFires extends SimpleBehaviour {

        private FiremenCoordinator fc = FiremenCoordinator.this;
        
        private void getResponse() {
            boolean isInfoReceivedOk = false;
            while(!isInfoReceivedOk) {
                ACLMessage response = fc.messageList.getMessage();
                if(response != null) {
                    switch(response.getPerformative()) {
                        case ACLMessage.AGREE:
                            fc.log("AGREE received from " + response.getSender().getLocalName());
                            break;
                        case ACLMessage.INFORM:
                            try {
                                MessageContent mc = (MessageContent) response.getContentObject();
                                if(mc.getMessageType() == MessageType.INFORM_NEW_STEP) {
                                    fc.log("State new information received from " + response.getSender().getLocalName());
                                    Object[] data = (Object[])mc.getContent();
                                    fc.game = (GameSettings) data[0];
                                    fc.newFires = (HashMap<BuildingCell, Integer>) data[1];
                                    if(fc.newFires != null) {
                                        Set<BuildingCell> newFirePositions = fc.newFires.keySet();
                                        newFirePositions.forEach(fp -> {
                                            fc.log("----------------------------------");
                                            fc.log("New fires found !!!");
                                            fc.log("Row: " + fp.getRow());
                                            fc.log("Column: " + fp.getCol());
                                            fc.log("----------------------------------");
                                        });
                                    }
                                    //Others here
                                    
                                    isInfoReceivedOk = true;
                                }
                                else {
                                    fc.messageList.addMessage(response);
                                }
                            }
                            catch(Exception ex) {
                                ex.printStackTrace();
                                fc.messageList.addMessage(response);
                            }
                            break;
                        case ACLMessage.FAILURE:
                            break;
                        default:
                            fc.messageList.addMessage(response);
                    }
                }
            }
            fc.messageList.endRetrieval();
        }        
        
        @Override
        public void action() {
            this.getResponse();
        }

        @Override
        public boolean done() {
            return true;
        }
        
    }
    /*
     * Inform that it finish the process of the step
     */
    private void informStepCoordinator() {
        ACLMessage stepMsg = new ACLMessage(ACLMessage.INFORM);
        stepMsg.clearAllReceiver();
        stepMsg.addReceiver(this.coordinatorAgent);
        try {
            stepMsg.setContentObject(new MessageContent(MessageType.DONE, null));
        } catch (Exception e) {
            e.printStackTrace();
        }
        send(stepMsg);
    }
    
    private void selectWinners()
    {
        ACLMessage initialRequest = new ACLMessage(ACLMessage.INFORM);
        initialRequest.setSender(getAID());
        initialRequest.clearAllReceiver();
        Object[] dataToFiremen = new Object[2];
        
        int i = 0;
        Map<AID, Integer> temporaryMap; //we create new map for each fire
        for(BuildingCell fireCell : this.newFires.keySet()) // we new fires to temporary map 
        {
            temporaryMap = new HashMap<>();
            for(Entry<AID, List<Integer>> entry : this.firemanResponses.entrySet()) // we new fires to temporary map 
            {
                temporaryMap.put(entry.getKey(), entry.getValue().get(i));
            }
            
            temporaryMap = (HashMap)sortByValues((HashMap) temporaryMap); // we sort the values
           
            int j = 0;
            int equalFires = 0;
            if((firesTakenCareOf.isEmpty())&&(newFires.size()==1))//first time we send all firemen to same fire 
            {
                equalFires = numberOfFiremen;
            }else
            {
                equalFires = (int)((firesTakenCareOf.size()+newFires.size())/numberOfFiremen);
            }
            for(AID entry : temporaryMap.keySet()) 
            {
                if(j<equalFires) // we only sent limited number of fires 
                {
                   dataToFiremen = new Object[2];
                   dataToFiremen[0] = fireCell; // we send fire cell
                   dataToFiremen[1] = j;        // and on which winner they were

                   initialRequest.addReceiver(entry); 
                      try {
                          initialRequest.setContentObject(new MessageContent(MessageType.GO_TO_THIS_FIRE, dataToFiremen));
                      } catch (IOException ex) {
                          Logger.getLogger(FiremenCoordinator.class.getName()).log(Level.SEVERE, null, ex);
                      }

                   send(initialRequest);
                   initialRequest.clearAllReceiver();
                   j++;
                }
            }
            i++;
            firesTakenCareOf.put(fireCell, fireCell.getBurnedRatio()); //we add fire to fires that are taken care of 
            newFires.remove(fireCell); // remove new fire;
        }
        
        
        firemanResponses= new HashMap<>();
    }
    
    
    private void newFires()
    {
        newFires = new HashMap<>();
        List<BuildingCell> tmpList = new ArrayList<>();
        
        for(Entry<BuildingCell, Integer> entry : this.game.getFireList().entrySet()) // we new fires to temporary map 
        {
            if(!firesTakenCareOf.containsKey(entry.getKey()))
            {
                tmpList.add(entry.getKey());
                newFires.put(entry.getKey(), entry.getValue());
            }
        }
        
        ACLMessage initialRequest = new ACLMessage(ACLMessage.CFP);
        initialRequest.setSender(getAID());
        initialRequest.clearAllReceiver();
        ServiceDescription searchCriterion = new ServiceDescription();
        searchCriterion.setType(AgentType.FIREMAN.toString());  
        Map<AgentType, List<Cell>> a = game.getAgentList();
        List<Cell> FIR = a.get(AgentType.FIREMAN);
        int i = 1;
        for (Cell FIR1 : FIR) {
            searchCriterion.setName("firemenAgent" + i);
            initialRequest.addReceiver(UtilsAgents.searchAgent(this, searchCriterion));
            i++;
        }

       try {

           initialRequest.setContentObject(new MessageContent(MessageType.NEW_FIRES, newFires));
          // log("Request message content:" + initialRequest.getContent());
       } catch (Exception e) {
           e.printStackTrace();
       }
       
       firemanResponses = new HashMap<>();
       
       this.send(initialRequest); 
    }
    /**
     * Update the game settings.
     *
     * @param game current game settings.
     */
    public void setGame(GameSettings game) {
        this.game = game;
    }
    
    public int getNumberOfFiremen() {
        return numberOfFiremen;
    }

    public void setNumberOfFiremen(int numberOfFiremen) {
        this.numberOfFiremen = numberOfFiremen;
    }
    
    /**
     * Gets the current game settings.
     *
     * @return the current game settings.
     */
    public GameSettings getGame() {
        return this.game;
    }
    
    public Map<BuildingCell, Integer> getFiresTakenCareOf() {
        return firesTakenCareOf;
    }

    public void setFiresTakenCareOf(Map<BuildingCell, Integer> firesTakenCareOf) {
        this.firesTakenCareOf = firesTakenCareOf;
    }
    
    private static HashMap sortByValues(HashMap map)
    {
        List tmp = new LinkedList(map.entrySet());
        
        Collections.sort(tmp, new Comparator(){

            @Override
            public int compare(Object o1, Object o2) {
                return ((Comparable)((Map.Entry)(o1)).getValue()).compareTo(((Map.Entry)(o2)).getValue());
               
            }
            
        });
        HashMap sorted = new LinkedHashMap();
        for(Iterator it = tmp.iterator(); it.hasNext();){
        Map.Entry entry= (Map.Entry)it.next();
        sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }
}
