package adf.sample.extaction;


import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.debug.DebugData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.sample.SampleModuleKey;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionRefill extends ExtAction{
    private final int maxWater;
    private final int maxPower;
    private EntityID[] targets;

    public ActionRefill(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DebugData debugData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, debugData);
        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.maxPower = scenarioInfo.getFireExtinguishMaxSum();
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
                return this;
            }
            // fix
            int waterLimit = this.maxPower;//((this.maxWater / 10) * 2);
            if(water < waterLimit) {
                PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
                pathPlanning.setFrom(agentInfo.getPosition());
                if(location.getStandardURN().equals(StandardEntityURN.HYDRANT)) {
                    if (this.targets != null) {
                        pathPlanning.setDestination(this.targets);
                    } else {
                        pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
                    }
                    pathPlanning.calc();
                    List<EntityID> path = pathPlanning.getResult();
                    this.result = path != null ? new ActionMove(path) : new ActionRest();
                } else {
                    if (this.targets != null) {
                        pathPlanning.setDestination(this.targets);
                    } else {
                        pathPlanning.setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
                    }
                    pathPlanning.calc();
                    List<EntityID> path = pathPlanning.getResult();
                    if (path == null) {
                        pathPlanning.setFrom(agentInfo.getPosition()).setDestination(worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
                        path = pathPlanning.calc().getResult();
                    }
                    this.result = path != null ? new ActionMove(path) : null;
                }
            }
        }
        return this;
    }
}
