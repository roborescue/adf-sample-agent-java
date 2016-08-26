package adf.sample.extaction;


import adf.agent.action.common.ActionMove;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import adf.sample.SampleModuleKey;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class ActionFireFighting extends ExtAction {

    private int maxExtinguishDistance;
    private int maxExtinguishPower;
    private EntityID[] targets;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.targets = null;
        this.maxExtinguishDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.targets = targets;
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        if(this.targets == null || this.targets.length == 0) {
            return this;
        }

        FireBrigade fireBrigade = (FireBrigade)this.agentInfo.me();
        if(StandardEntityURN.REFUGE == this.worldInfo.getPosition(fireBrigade).getStandardURN()) {
            PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.targets);
            pathPlanning.calc();
            List<EntityID> path = pathPlanning.getResult();
            if(path != null && !path.isEmpty()) {
                this.result = new ActionMove(path);
                return this;
            }
        }

        List<StandardEntity> neighbourBuilding = new ArrayList<>();
        for(EntityID target : this.targets) {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if (this.worldInfo.getDistance(fireBrigade, entity) < this.maxExtinguishDistance) {
                neighbourBuilding.add(entity);
            }
        }

        if(neighbourBuilding.size() > 0) {
            if(neighbourBuilding.size() > 1) {
                neighbourBuilding.sort(new DistanceSorter(this.worldInfo, fireBrigade));
            }
            this.result = new ActionExtinguish(neighbourBuilding.get(0).getID(), this.maxExtinguishPower);
        } else {
            PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.targets);
            pathPlanning.calc();
            List<EntityID> path = pathPlanning.getResult();
            if (path != null && path.size() > 0) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }

    private class DistanceSorter implements Comparator<StandardEntity> {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference) {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b) {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }
}
