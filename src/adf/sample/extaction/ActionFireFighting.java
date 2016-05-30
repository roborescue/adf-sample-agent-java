package adf.sample.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ActionFireFighting extends ExtAction {

    private WorldInfo worldInfo;
    private AgentInfo agentInfo;
    private int maxDistance;
    private int maxPower;
    private EntityID target;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager);
        this.worldInfo = worldInfo;
        this.agentInfo = agentInfo;
        this.target = null;
        this.maxDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxPower = scenarioInfo.getFireExtinguishMaxSum();
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.target = targets[0];
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = new ActionRest();
        if (worldInfo.getDistance(agentInfo.getID(), this.target) <= maxDistance) {
            this.result = new ActionExtinguish(this.target, maxPower);
        }
        else {
            List<EntityID> path = planPathToFire(this.target);
            if (path != null) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }

    private List<EntityID> planPathToFire(EntityID target) {
        // Try to get to anything within maxDistance of the target
        Collection<EntityID> targets = this.worldInfo.getObjectIDsInRange(target, maxDistance);
        if (targets.isEmpty()) {
            return null;
        }
        PathPlanning pathPlanning = this.moduleManager.getModule("PathPlanning");
        pathPlanning.setFrom(this.agentInfo.getPosition()).setDestination(targets).calc();
        return pathPlanning.getResult();
    }
}
