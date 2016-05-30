package adf.sample.extaction;


import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionRefill extends ExtAction{
    private final int maxWater;
    private EntityID[] targets;

    public ActionRefill(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager);
        this.maxWater = scenarioInfo.getFireTankMaximum();
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.targets = targets;
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        // Are we currently filling with water?
        if (agentInfo.isWaterDefined()) {
            int water = agentInfo.getWater();
            Area location = agentInfo.getPositionArea();
            // refuge
            if(water < this.maxWater && location.getStandardURN().equals(StandardEntityURN.REFUGE)) {
                this.result = new ActionRest();
            }
            // hydrant
            int hydrantWaterLimit = ((this.maxWater / 10) * 2);
            if(water < hydrantWaterLimit && location.getStandardURN().equals(StandardEntityURN.HYDRANT)) {
                // move refuge
                PathPlanning pathPlanning = this.moduleManager.getModule("PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                if(this.targets != null) {
                    pathPlanning.setDestination(this.targets);
                }else {
                    pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
                }
                pathPlanning.calc();
                List<EntityID> path = pathPlanning.getResult();
                this.result = path != null ? new ActionMove(path) : new ActionRest();
            }
        }

        // Are we out of water?
        if (agentInfo.isWaterDefined() && agentInfo.getWater() == 0) {
            // Head for a refuge
            PathPlanning pathPlanning = this.moduleManager.getModule("PathPlanning");
            pathPlanning.setFrom(agentInfo.getPosition());
            pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            pathPlanning.calc();
            List<EntityID> path = pathPlanning.getResult();
            // Head for a hydrant
            if (path == null) {
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
                pathPlanning.calc();
                path = pathPlanning.getResult();
            }
            if(path != null) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}
