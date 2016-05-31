package adf.sample.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.sample.SampleModuleKey;
import adf.sample.util.DistanceSorter;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ActionFireFighting extends ExtAction {

    private int maxDistance;
    private int maxPower;
    private EntityID[] targets;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager);
        this.targets = null;
        this.maxDistance = scenarioInfo.getFireExtinguishMaxDistance();
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
        if(this.targets == null) {
            return this;
        }
        List<EntityID> distantBuilding = new ArrayList<>();
        List<StandardEntity> neighbourBuilding = new ArrayList<>();
        for(EntityID target : this.targets) {
            if (worldInfo.getDistance(agentInfo.getID(), target) <= maxDistance) {
                neighbourBuilding.add(this.worldInfo.getEntity(target));
            } else {
                distantBuilding.add(target);
            }
        }
        if(neighbourBuilding.size() > 0) {
            neighbourBuilding.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            this.result = new ActionExtinguish(neighbourBuilding.get(0).getID(), maxPower);
        } else {
            List<EntityID> path = planPathToFire(distantBuilding);
            if (path != null) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }

    private List<EntityID> planPathToFire(List<EntityID> targetList) {
        // Try to get to anything within maxDistance of the targets
        Set<EntityID> targets = new HashSet<>();
        for(EntityID id : targetList) {
            targets.addAll(this.worldInfo.getObjectIDsInRange(id, maxDistance));
        }
        if (targets.isEmpty()) {
            return null;
        }
        PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        pathPlanning.setFrom(this.agentInfo.getPosition()).setDestination(targets).calc();
        return pathPlanning.getResult();
    }
}
