package adf.sample.extaction;


import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.algorithm.PathPlanning;
import adf.component.extaction.ExtAction;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionRefill extends ExtAction{
    private final int maxWater;
    private WorldInfo worldInfo;
    private AgentInfo agentInfo;
    private PathPlanning pathPlanning;

    public ActionRefill(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PathPlanning pathPlanning) {
        super();
        this.worldInfo = worldInfo;
        this.agentInfo = agentInfo;
        this.pathPlanning = pathPlanning;
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
                this.pathPlanning.setFrom(agentInfo.getPosition());
                this.pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
                List<EntityID> path = this.pathPlanning.getResult();
                this.result = path != null ? new ActionMove(path) : new ActionRest();
            }
        }

        // Are we out of water?
        if (agentInfo.isWaterDefined() && agentInfo.getWater() == 0) {
            // Head for a refuge
            this.pathPlanning.setFrom(agentInfo.getPosition());
            this.pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = this.pathPlanning.getResult();
            // Head for a hydrant
            if (path == null) {
                this.pathPlanning.setFrom(agentInfo.getPosition());
                this.pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
                path = this.pathPlanning.getResult();
            }
            if(path != null) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}
