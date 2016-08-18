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
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionRefill extends ExtAction{
    private final int maxWater;
    private final int maxExtinguishPower;

    private int thresholdCompleted;
    private int thresholdRefill;

    private EntityID[] targets;

    public ActionRefill(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DebugData debugData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, debugData);
        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        //use DebugData
        this.thresholdCompleted = (this.maxWater / 10) * debugData.getInteger("fire.threshold.refill", 10);
        this.thresholdRefill = this.maxExtinguishPower;
        this.targets = null;
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.targets = targets;
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        FireBrigade fireBrigade = (FireBrigade)this.agentInfo.me();
        if(!fireBrigade.isWaterDefined()) {
            return this;
        }

        // Are we currently filling with water?
        int water = fireBrigade.getWater();
        StandardEntityURN positionURN = this.worldInfo.getPosition(fireBrigade).getStandardURN();
        // refuge
        if(water < this.thresholdCompleted && positionURN.equals(StandardEntityURN.REFUGE)) {
            this.result = new ActionRest();
            return this;
        }
        if(water > this.thresholdRefill) {
            return this;
        }
        PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        pathPlanning.setFrom(fireBrigade.getPosition());
        if (this.targets != null && this.targets.length > 0) {
            pathPlanning.setDestination(this.targets);
        } else {
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
        }
        pathPlanning.calc();
        List<EntityID> path = pathPlanning.getResult();
        if(positionURN.equals(StandardEntityURN.HYDRANT)) {
            if(path != null && path.size() > 0) {
                this.result = new ActionMove(path);
            } else {
                this.result = new ActionRest();
            }
        } else {
            if (path == null || path.isEmpty()) {
                pathPlanning.setFrom(fireBrigade.getPosition());
                pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
                pathPlanning.calc();
                path = pathPlanning.getResult();
            }
            if(path != null && path.size() > 0) {
                this.result = new ActionMove(path);
            } else {
                this.result = null;
            }
        }
        return this;
    }
}
