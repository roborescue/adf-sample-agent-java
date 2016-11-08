package adf.sample.extaction;


import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.HYDRANT;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class ActionFireFighting extends ExtAction {
    private PathPlanning pathPlanning;
    private int maxExtinguishDistance;
    private int maxExtinguishPower;
    private int thresholdRest;
    private int kernelTime;
    private int refillCompleted;
    private int refillRequest;
    private boolean refillFlag;

    private Collection<EntityID> targets;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.maxExtinguishDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        this.thresholdRest = developData.getInteger("ActionFireFighting.rest", 100);
        this.kernelTime = scenarioInfo.getKernelTimesteps();
        int maxWater = scenarioInfo.getFireTankMaximum();
        this.refillCompleted = (maxWater / 10) * developData.getInteger("ActionFireFighting.refill.completed", 10);
        this.refillRequest = this.maxExtinguishPower * developData.getInteger("ActionFireFighting.refill.request", 1);
        this.refillFlag = false;

        this.targets = new ArrayList<>();

        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("ActionExtMove.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("ActionExtMove.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("ActionExtMove.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
        }
    }

    public ExtAction precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        return this;
    }

    public ExtAction resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        return this;
    }

    public ExtAction preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        return this;
    }

    public ExtAction updateInfo(MessageManager messageManager){
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        return this;
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.targets.clear();
        if(targets != null) {
            for(EntityID id : targets) {
                StandardEntity entity = this.worldInfo.getEntity(id);
                if(entity instanceof Building) {
                    this.targets.add(id);
                }
            }
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        FireBrigade agent = (FireBrigade)this.agentInfo.me();

        this.refillFlag = this.needRefill(agent, this.refillFlag);
        if(this.refillFlag) {
            this.result = this.calcRefill(agent, this.pathPlanning, this.targets);
            if(this.result != null) {
                return this;
            }
        }

        if(this.needRest(agent)) {
            this.result = this.calcRefugeAction(agent, this.pathPlanning, this.targets, false);
            if(this.result != null) {
                return this;
            }
        }

        if(this.targets == null || this.targets.isEmpty()) {
            return this;
        }
        this.result = this.calcExtinguish(agent, this.pathPlanning, this.targets);
        return this;
    }

    private Action calcExtinguish(FireBrigade agent, PathPlanning pathPlanning, Collection<EntityID> targets) {
        EntityID agentPosition = agent.getPosition();
        StandardEntity positionEntity = this.worldInfo.getPosition(agent);
        if(StandardEntityURN.REFUGE == positionEntity.getStandardURN()) {
            Action action = this.getMoveAction(pathPlanning, agentPosition, targets);
            if(action != null) {
                return action;
            }
        }

        List<StandardEntity> neighbourBuilding = new ArrayList<>();
        for(EntityID target : targets) {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if(entity instanceof Building) {
                if (this.worldInfo.getDistance(positionEntity, entity) < this.maxExtinguishDistance) {
                    neighbourBuilding.add(entity);
                }
            }
        }

        if(neighbourBuilding.size() > 0) {
            neighbourBuilding.sort(new DistanceSorter(this.worldInfo, agent));
            return new ActionExtinguish(neighbourBuilding.get(0).getID(), this.maxExtinguishPower);
        }
        return this.getMoveAction(pathPlanning, agentPosition, targets);
    }

    private Action getMoveAction(PathPlanning pathPlanning, EntityID from, Collection<EntityID> targets) {
        pathPlanning.setFrom(from);
        pathPlanning.setDestination(targets);
        List<EntityID> path = pathPlanning.calc().getResult();
        if(path != null && path.size() > 0) {
            StandardEntity entity = this.worldInfo.getEntity(path.get(path.size() - 1));
            if(entity instanceof Building) {
                if(entity.getStandardURN() != StandardEntityURN.REFUGE) {
                    path.remove(path.size() - 1);
                }
            }
            return new ActionMove(path);
        }
        return null;
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

    private boolean needRefill(FireBrigade agent, boolean refillFlag) {
        if(refillFlag) {
            StandardEntityURN position = this.worldInfo.getEntity(agent.getPosition()).getStandardURN();
            return !(position == REFUGE || position == HYDRANT) || agent.getWater() < this.refillCompleted;
        }
        return  agent.getWater() <= this.refillRequest;
    }

    private boolean needRest(Human agent) {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if(hp == 0 || damage == 0) {
            return false;
        }
        int step = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
        return (step + this.agentInfo.getTime()) < this.kernelTime || damage >= this.thresholdRest;
    }

    private Action calcRefill(FireBrigade agent, PathPlanning pathPlanning, Collection<EntityID> targets) {
        EntityID position = agent.getPosition();
        StandardEntityURN positionURN = this.worldInfo.getPosition(position).getStandardURN();
        if(positionURN == REFUGE) {
            return new ActionRefill();
        }
        Action action = this.calcRefugeAction(agent, pathPlanning, targets, true);
        if(action != null) {
            return action;
        }
        action = this.calcHydrantAction(agent, pathPlanning, targets);
        if(action != null) {
            if(positionURN == HYDRANT && action.getClass().equals(ActionMove.class)) {
                pathPlanning.setFrom(position);
                pathPlanning.setDestination(targets);
                double currentDistance = pathPlanning.calc().getDistance();
                List<EntityID> path = ((ActionMove)action).getPath();
                pathPlanning.setFrom(path.get(path.size() - 1));
                pathPlanning.setDestination(targets);
                double newHydrantDistance = pathPlanning.calc().getDistance();
                if(currentDistance <= newHydrantDistance) {
                    return new ActionRefill();
                }
            }
            return action;
        }
        return null;
    }

    private Action calcRefugeAction(Human human, PathPlanning pathPlanning, Collection<EntityID> targets, boolean isRefill) {
        return this.calcSupply(human, pathPlanning, this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE), targets, isRefill);
    }

    private Action calcHydrantAction(Human human, PathPlanning pathPlanning, Collection<EntityID> targets) {
        Collection<EntityID> hydrants = this.worldInfo.getEntityIDsOfType(HYDRANT);
        hydrants.remove(human.getPosition());
        return this.calcSupply(human, pathPlanning, hydrants, targets, true);
    }

    private Action calcSupply(Human human, PathPlanning pathPlanning, Collection<EntityID> supplyPositions, Collection<EntityID> targets, boolean isRefill) {
        EntityID position = human.getPosition();
        int size = supplyPositions.size();
        if(supplyPositions.contains(position)) {
            return isRefill ? new ActionRefill() : new ActionRest();
        }
        List<EntityID> firstResult = null;
        while(supplyPositions.size() > 0) {
            pathPlanning.setFrom(position);
            pathPlanning.setDestination(supplyPositions);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                if (firstResult == null) {
                    firstResult = new ArrayList<>(path);
                    if(targets == null || targets.isEmpty()) {
                        break;
                    }
                }
                EntityID supplyPositionID = path.get(path.size() - 1);
                pathPlanning.setFrom(supplyPositionID);
                pathPlanning.setDestination(targets);
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0) {
                    return new ActionMove(path);
                }
                supplyPositions.remove(supplyPositionID);
                //remove failed
                if (size == supplyPositions.size()) {
                    break;
                }
                size = supplyPositions.size();
            } else {
                break;
            }
        }
        return firstResult != null ? new ActionMove(firstResult) : null;
    }
}

