package adf.sample.extaction;


import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
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
    private int maxExtinguishDistance;
    private int maxExtinguishPower;
    private int maxWater;

    private int thresholdCompleted;
    private int thresholdRefill;

    private Collection<EntityID> targets;
    private boolean isRefill;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.targets = new ArrayList<>();
        this.isRefill = false;
        this.maxExtinguishDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.thresholdCompleted = (this.maxWater / 10) * developData.getInteger("fire.threshold.completed", 10);
        this.thresholdRefill = this.maxExtinguishPower * developData.getInteger("fire.threshold.refill", 1);
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.targets.clear();
        this.isRefill = false;
        if(targets != null) {
            for(EntityID id : targets) {
                StandardEntity entity = this.worldInfo.getEntity(id);
                if(entity instanceof Building) {
                    if(entity.getStandardURN() == StandardEntityURN.REFUGE) {
                        this.isRefill = true;
                    }
                    this.targets.add(id);
                } else if(entity.getStandardURN() == StandardEntityURN.HYDRANT) {
                    this.isRefill = true;
                    this.targets.add(id);
                }
            }
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        if(this.targets == null || this.targets.isEmpty()) {
            return this;
        }

        FireBrigade fireBrigade = (FireBrigade)this.agentInfo.me();
        EntityID agentPosition = fireBrigade.getPosition();
        PathPlanning pathPlanning = this.moduleManager.getModule("TacticsFire.PathPlanning");

        if(this.isRefill) {
            if(this.targets.contains(agentPosition)) {
                this.result = new ActionRefill();
                return this;
            }
            this.result = this.getMoveAction(pathPlanning, agentPosition, this.targets);
            if(this.result != null) {
                return this;
            }
            this.result = this.getRefillAction(pathPlanning, agentPosition, REFUGE);
            if(this.result == null) {
                this.result = this.getRefillAction(pathPlanning, agentPosition, HYDRANT);
            }
            if(this.result != null) {
                return this;
            }
        }

        if(StandardEntityURN.REFUGE == this.worldInfo.getPosition(fireBrigade).getStandardURN()) {
            this.result = this.getMoveAction(pathPlanning, agentPosition, this.targets);
            if(this.result != null) {
                return this;
            }
        }

        List<StandardEntity> neighbourBuilding = new ArrayList<>();
        for(EntityID target : this.targets) {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if(entity instanceof Building) {
                if (this.worldInfo.getDistance(fireBrigade, entity) < this.maxExtinguishDistance) {
                    neighbourBuilding.add(entity);
                }
            }
        }

        if(neighbourBuilding.size() > 0) {
            neighbourBuilding.sort(new DistanceSorter(this.worldInfo, fireBrigade));
            this.result = new ActionExtinguish(neighbourBuilding.get(0).getID(), this.maxExtinguishPower);
        } else {
            this.result = this.getMoveAction(pathPlanning, agentPosition, this.targets);
        }
        return this;
    }

    private Action getMoveAction(PathPlanning pathPlanning, EntityID from, Collection<EntityID> targets) {
        pathPlanning.setFrom(from);
        pathPlanning.setDestination(targets);
        List<EntityID> path = pathPlanning.calc().getResult();
        if(path != null && !path.isEmpty()) {
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

    private Action getRefillAction(PathPlanning pathPlanning, EntityID agentPosition, StandardEntityURN areaURN) {
        Collection<EntityID> refillArea = this.worldInfo.getEntityIDsOfType(areaURN);
        if(refillArea.contains(agentPosition)) {
            return new ActionRefill();
        }
        pathPlanning.setFrom(agentPosition);
        pathPlanning.setDestination(refillArea);
        List<EntityID> path = pathPlanning.calc().getResult();
        if (path != null) {
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

    private EntityID calcRefill() {
        FireBrigade fireBrigade = (FireBrigade)this.agentInfo.me();
        int water = fireBrigade.getWater();
        StandardEntityURN positionURN = this.worldInfo.getPosition(fireBrigade).getStandardURN();
        if(positionURN.equals(StandardEntityURN.REFUGE) && water < this.thresholdCompleted) {
            return fireBrigade.getPosition();
        }
        PathPlanning pathPlanning = this.moduleManager.getModule("adf.sample.module.algorithm.SamplePathPlanning");
        if(positionURN.equals(StandardEntityURN.HYDRANT) && water < this.thresholdCompleted) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = pathPlanning.calc().getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            } else {
                return fireBrigade.getPosition();
            }
        }
        if (water <= this.thresholdRefill) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = pathPlanning.calc().getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            }
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
            path = pathPlanning.calc().getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            }
        }
        return null;
    }
}
