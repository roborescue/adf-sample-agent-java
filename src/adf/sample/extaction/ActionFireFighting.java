package adf.sample.extaction;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.component.algorithm.path.PathPlanner;
import adf.component.extaction.ExtAction;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ActionFireFighting extends ExtAction {

    private WorldInfo worldInfo;
    private AgentInfo agentInfo;
    private PathPlanner pathPlanner;
    private int maxDistance;
    private int maxPower;
    private EntityID target;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PathPlanner pathPlanner, EntityID target) {
        super();
        this.worldInfo = worldInfo;
        this.agentInfo = agentInfo;
        this.pathPlanner = pathPlanner;
        this.target = target;
        this.maxDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxPower = scenarioInfo.getFireExtinguishMaxSum();
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
        this.pathPlanner.setFrom(this.agentInfo.getPosition());
        this.pathPlanner.setDist(targets);
        return this.pathPlanner.getResult();
    }
}
