/**
 *  IMAS base code for the practical work.
 *  Copyright (C) 2014 DEIM - URV
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cat.urv.imas.behaviour.central;

import cat.urv.imas.agent.AgentType;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import cat.urv.imas.agent.CentralAgent;
import cat.urv.imas.map.Cell;
import cat.urv.imas.map.StreetCell;
import cat.urv.imas.onthology.MessageContent;
import cat.urv.imas.utils.MessageType;
import jade.core.AID;
import java.util.List;
import java.util.Map;

/**
 * A request-responder behaviour for Central agent, answering to queries
 * from the Coordinator agent. The Coordinator Agent sends a REQUEST of the whole
 * game information and the Central Agent sends an AGREE and then an INFORM
 * with the city information.
 */
public class RequestResponseBehaviour extends AchieveREResponder {

    /**
     * Sets up the Central agent and the template of messages to catch.
     *
     * @param agent The agent owning this behaviour
     * @param mt Template to receive future responses in this conversation
     */
    public RequestResponseBehaviour(CentralAgent agent, MessageTemplate mt) {
        super(agent, mt);
        agent.log("Waiting REQUESTs from authorized agents");
    }

    /**
     * When Central Agent receives a REQUEST message, it agrees. Only if
     * message type is AGREE, method prepareResultNotification() will be invoked.
     *
     * @param msg message received.
     * @return AGREE message when all was ok, or FAILURE otherwise.
     */
    @Override
    protected ACLMessage prepareResponse(ACLMessage msg) {
        CentralAgent agent = (CentralAgent)this.getAgent();
        ACLMessage reply = msg.createReply();
        try {
            MessageContent mc = (MessageContent) msg.getContentObject();
            switch(mc.getMessageType()) {
                case REQUEST_CITY_STATUS:
                    agent.log("Request received");
                    reply.setPerformative(ACLMessage.AGREE);
                    break;
            }
        } catch (Exception e) {
            reply.setPerformative(ACLMessage.FAILURE);
            agent.errorLog(e.getMessage());
            e.printStackTrace();
        }
        agent.log("Response being prepared");
        return reply;
    }

    /**
     * After sending an AGREE message on prepareResponse(), this behaviour
     * sends an INFORM message with the whole game settings.
     *
     * NOTE: This method is called after the response has been sent and only when one
     * of the following two cases arise: the response was an agree message OR no
     * response message was sent.
     *
     * @param msg ACLMessage the received message
     * @param response ACLMessage the previously sent response message
     * @return ACLMessage to be sent as a result notification, of type INFORM
     * when all was ok, or FAILURE otherwise.
     */
    @Override
    protected ACLMessage prepareResultNotification(ACLMessage msg, ACLMessage response) {
        CentralAgent agent = (CentralAgent)this.getAgent();
        ACLMessage reply = null;

        try {
            MessageContent mc = (MessageContent) msg.getContentObject();
            switch(mc.getMessageType()) {
                case REQUEST_CITY_STATUS:
                    if(mc.getContent()!=null)
                    {
                        agent.updateMoves((Map<AID, Object[]>)mc.getContent());
                    }
                    reply = sendGameStatus(msg);
                    break;
            }
        } catch (Exception e) {
            reply.setPerformative(ACLMessage.FAILURE);
            agent.errorLog(e.getMessage());
            e.printStackTrace();
        }

        return reply;
    }

    private ACLMessage sendGameStatus(ACLMessage msg) {

        // it is important to make the createReply in order to keep the same context of
        // the conversation
        CentralAgent agent = (CentralAgent)this.getAgent();
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.INFORM);

        Map<String, Object> stepData = agent.simulationStep(); //update the game
        stepData.put("game", agent.getGame());
       
        agent.updateGUI(); //update gui

        try {
            MessageContent mc = new MessageContent(MessageType.INFORM_CITY_STATUS, stepData);
            reply.setContentObject(mc);
        } catch (Exception e) {
            reply.setPerformative(ACLMessage.FAILURE);
            agent.errorLog(e.toString());
            e.printStackTrace();
        }
        agent.log("Game settings sent");
        return reply;
    }

    /**
     * No need for any specific action to reset this behaviour
     */
    @Override
    public void reset() {
    }

}
