/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cat.urv.imas.agent;

import cat.urv.imas.map.Cell;
import cat.urv.imas.map.StreetCell;
import cat.urv.imas.onthology.GameSettings;
import org.newdawn.slick.util.pathfinding.Path;
import cat.urv.imas.utils.Utils;
/**
 *
 * @author mhj
 */
public abstract class NavigatorAgent extends ImasAgent {

    protected GameSettings game;
    
    protected Cell agentPosition;
    
    protected Cell targetPosition;
    
    protected Path shortestPath;
    
    protected int currentStep = -1;
    
    public NavigatorAgent(AgentType type) {
        super(type);
    }
    
    protected boolean checkCollisions() {
        return false;
    }
    
    public float findShortestPath() {
        this.shortestPath = Utils.getShortestPath(this.game.getMap(), agentPosition, targetPosition);
        this.currentStep = -1;
        return this.shortestPath.getLength();
    }
    
    public Cell[] getPath() {
        if(this.shortestPath == null) {
            return null;
        }
        
        int n = shortestPath.getLength();
        Cell[] path = new Cell[n];
        for(int i = 0; i < n; i++) {
            path[i] = game.get(shortestPath.getX(i), shortestPath.getY(i));
        }
        return path;
    } 
    
    
    public float getPathCost() {
        if(this.shortestPath == null) {
            return 0;
        }
        return this.shortestPath.getLength();
    }
    
    protected boolean moveStep() {
        if(this.shortestPath == null) {
            return false;
        }
        
        //Already in the target
        if(this.currentStep == this.shortestPath.getLength() - 1 || 
                (this.agentPosition.getRow() == this.targetPosition.getRow() &&
                this.agentPosition.getCol() == this.targetPosition.getCol())) {
            
            return false;
        }
        int s = this.currentStep + 1;
        Path.Step step = shortestPath.getStep(s);
        StreetCell cell =(StreetCell) this.game.get(step.getX(), step.getY());
        if(cell.isThereAnAgent()) {
            this.findShortestPath();
            return moveStep();
        }
        else {
            this.currentStep = s;
            this.agentPosition = cell;
            return true;
        }
    }
    
    @Override
    protected void setup() {
        
        this.setEnabledO2ACommunication(true, 1);
        
         Object[] args = this.getArguments();
         this.agentPosition = (Cell)args[0];
    }

    public void setGame(GameSettings game) {
        this.game = game;
    }

    public GameSettings getGame() {
        return game;
    }

    public void setAgentPosition(Cell agentPosition) {
        this.agentPosition = agentPosition;
    }

    public Cell getAgentPosition() {
        return agentPosition;
    }

    public void setTargetPosition(Cell targetPosition) {
        this.targetPosition = targetPosition;
    }

    public Cell getTargetPosition() {
        return targetPosition;
    }
    
    
    
}
