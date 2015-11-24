package adf.sample.ambulance.tactics.extaction;

import adf.agent.info.*;
import adf.agent.platoon.action.ambulance.*;
import adf.agent.platoon.action.common.*;
import adf.agent.platoon.extaction.ExtAction;
import adf.algorithm.path.*;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionTransport extends ExtAction {

    private Human next;
    private WorldInfo worldInfo;
    private AgentInfo agentInfo;
    private PathPlanner pathPlanner;

    public ActionTransport(Human target, WorldInfo wi, AgentInfo ai, PathPlanner planner) {
        super();
        this.next = target;
        this.worldInfo = wi;
        this.agentInfo = ai;
        this.pathPlanner = planner;
    }

    @Override
    public ExtAction calc() {
        this.result = new ActionRest();
        if (next.getPosition().equals(agentInfo.getPosition())) {
            // Targets in the same place might need rescueing or loading
            if ((next instanceof Civilian) && next.getBuriedness() == 0 && !(agentInfo.getLocation() instanceof Refuge)) {
                // Load
                //Logger.info("Loading " + next);
                //sendLoad(time, next.getID());
                //return new ActionLoad(next.getID());
                this.result = new ActionLoad(next.getID());
            }
            if (next.getBuriedness() > 0) {
                // Rescue
                //Logger.info("Rescueing " + next);
                //sendRescue(time, next.getID());
                //return new ActionRescue(next.getID());
                this.result = new ActionRescue(next.getID());
            }
        }
        else {
            // Try to move to the target
            //List<EntityID> path = search.breadthFirstSearch(me().getPosition(), next.getPosition());
            this.pathPlanner.setFrom(agentInfo.getPosition());
            this.pathPlanner.setDist(next.getPosition());
            List<EntityID> path = this.pathPlanner.getResult();
            if (path != null) {
                //Logger.info("Moving to target");
                //sendMove(time, path);
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}