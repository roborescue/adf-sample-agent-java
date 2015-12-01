package adf.sample.extaction;


import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.algorithm.pathplanning.PathPlanner;
import adf.component.extaction.ExtAction;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionRefill extends ExtAction{
    private final int maxWater;
    private WorldInfo worldInfo;
    private AgentInfo agentInfo;
    private PathPlanner pathPlanner;

    public ActionRefill(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PathPlanner pathPlanner) {
        super();
        this.worldInfo = worldInfo;
        this.agentInfo = agentInfo;
        this.pathPlanner = pathPlanner;
        this.maxWater = scenarioInfo.getFireTankMaximum();
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        // Are we currently filling with water?
        if (agentInfo.isWaterDefined()) {
            int water = agentInfo.getWater();
            Area location = agentInfo.getLocation();
            // refuge
            if(water < this.maxWater && location.getStandardURN().equals(StandardEntityURN.REFUGE)) {
                this.result = new ActionRest();
            }
            // hydrant
            int hydrantWaterLimit = ((this.maxWater / 10) * 2);
            if(water < hydrantWaterLimit && location.getStandardURN().equals(StandardEntityURN.HYDRANT)) {
                // move refuge
                this.pathPlanner.setFrom(agentInfo.getPosition());
                this.pathPlanner.setDist(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
                List<EntityID> path = this.pathPlanner.getResult();
                this.result = path != null ? new ActionMove(path) : new ActionRest();
            }
        }

        // Are we out of water?
        if (agentInfo.isWaterDefined() && agentInfo.getWater() == 0) {
            // Head for a refuge
            this.pathPlanner.setFrom(agentInfo.getPosition());
            this.pathPlanner.setDist(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = this.pathPlanner.getResult();
            // Head for a hydrant
            if (path == null) {
                this.pathPlanner.setFrom(agentInfo.getPosition());
                this.pathPlanner.setDist(worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
                path = this.pathPlanner.getResult();
            }
            if(path != null) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}
