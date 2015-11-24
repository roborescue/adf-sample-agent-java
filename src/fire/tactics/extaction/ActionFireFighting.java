package adf.sample.fire.tactics.extaction;

import adf.agent.info.*;
import adf.agent.platoon.action.common.ActionMove;
import adf.agent.platoon.action.fire.ActionExtinguish;
import adf.agent.platoon.extaction.ExtAction;
import adf.algorithm.path.PathPlanner;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class ActionFireFighting extends ExtAction{

    private static final String MAX_DISTANCE_KEY = "fire.extinguish.max-distance";
    private static final String MAX_POWER_KEY = "fire.extinguish.max-sum";

    private WorldInfo worldInfo;
    private AgentInfo agentInfo;
    private PathPlanner pathPlanner;
    private int maxDistance;
    private int maxPower;
    private EntityID target;

    public ActionFireFighting(EntityID target, WorldInfo wi, AgentInfo ai, PathPlanner pp) {
        super();
        this.target = target;
        this.worldInfo = wi;
        this.agentInfo = ai;
        this.pathPlanner = pp;
        this.maxDistance = agentInfo.config.getIntValue(MAX_DISTANCE_KEY);
        this.maxPower = agentInfo.config.getIntValue(MAX_POWER_KEY);
    }

    @Override
    public ExtAction calc() {
        this.result = null;//new ActionRest();
        if (worldInfo.world.getDistance(agentInfo.getID(), this.target) <= maxDistance) {
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
        Collection<StandardEntity> targets = this.worldInfo.world.getObjectsInRange(target, maxDistance);
        if (targets.isEmpty()) {
            return null;
        }

        List<EntityID> cvtList = new ArrayList<>();
        for(StandardEntity entity : targets) {
            cvtList.add(entity.getID());
        }
        this.pathPlanner.setFrom(this.agentInfo.getPosition());
        this.pathPlanner.setDist(cvtList);
        return this.pathPlanner.getResult();
        //return new ActionMove(path);
    }
}
