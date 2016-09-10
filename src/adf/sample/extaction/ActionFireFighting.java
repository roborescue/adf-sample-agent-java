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

public class ActionFireFighting extends ExtAction {
    private int maxExtinguishDistance;
    private int maxExtinguishPower;
    private Collection<EntityID> targets;
    private boolean isRefill;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.targets = null;
        this.isRefill = false;
        this.maxExtinguishDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();

    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.targets = new ArrayList<>();
        this.isRefill = false;
        if(targets != null) {
            for(EntityID id : targets) {
                StandardEntity entity = this.worldInfo.getEntity(id);
                if(entity instanceof Building) {
                    if(entity.getStandardURN() == StandardEntityURN.REFUGE) {
                        this.isRefill = true;
                    }
                    this.targets.add(id);
                } else if(entity instanceof Road) {
                    if(entity.getStandardURN() == StandardEntityURN.HYDRANT) {
                        this.isRefill = true;
                    }
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
        PathPlanning pathPlanning = this.moduleManager.getModule("TacticsFire.PathPlanning");

        if(this.isRefill) {
            if(this.targets.contains(fireBrigade.getPosition())) {
                this.result = new ActionRefill();
                return this;
            }
            this.result = this.getMoveAction(pathPlanning, fireBrigade.getPosition(), this.targets);
            if(this.result != null) {
                return this;
            }
        }

        if(StandardEntityURN.REFUGE == this.worldInfo.getPosition(fireBrigade).getStandardURN()) {
            this.result = this.getMoveAction(pathPlanning, fireBrigade.getPosition(), this.targets);
            if(this.result != null) {
                return this;
            }
        }

        List<StandardEntity> neighbourBuilding = new ArrayList<>();
        for(EntityID target : this.targets) {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if(entity instanceof Road) {
                continue;
            }
            if (this.worldInfo.getDistance(fireBrigade, entity) < this.maxExtinguishDistance) {
                neighbourBuilding.add(entity);
            }
        }

        if(neighbourBuilding.size() > 0) {
            neighbourBuilding.sort(new DistanceSorter(this.worldInfo, fireBrigade));
            this.result = new ActionExtinguish(neighbourBuilding.get(0).getID(), this.maxExtinguishPower);
        } else {
            this.result = this.getMoveAction(pathPlanning, fireBrigade.getPosition(), this.targets);
        }
        return this;
    }

    private Action getMoveAction(PathPlanning pathPlanning, EntityID from, Collection<EntityID> targets) {
        pathPlanning.setFrom(from);
        pathPlanning.setDestination(targets);
        pathPlanning.calc();
        List<EntityID> path = pathPlanning.getResult();
        if(path != null && !path.isEmpty()) {
            StandardEntity entity = this.worldInfo.getEntity(path.get(path.size() - 1));
            StandardEntityURN urn = entity.getStandardURN();
            if(urn != StandardEntityURN.REFUGE && urn != StandardEntityURN.HYDRANT) {
                if(entity instanceof Building) {
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
}
