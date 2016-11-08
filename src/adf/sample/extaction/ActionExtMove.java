package adf.sample.extaction;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
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
import java.util.List;

public class ActionExtMove extends ExtAction {
    private List<EntityID> searchTargets;

    private int thresholdRest;
    private int kernelTime;

    private PathPlanning pathPlanning;

    public ActionExtMove(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.searchTargets = new ArrayList<>();
        this.thresholdRest = developData.getInteger("ActionExtMove.rest", 100);
        this.kernelTime = scenarioInfo.getKernelTimesteps();

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
        this.searchTargets.clear();
        if(targets == null) {
            return this;
        }
        for(EntityID entityID : targets) {
            StandardEntity entity = this.worldInfo.getEntity(entityID);
            if(entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
                entity = this.worldInfo.getEntity(((Blockade)entity).getPosition());
            } else if(entity instanceof Human) {
                entity = this.worldInfo.getPosition((Human)entity);
            }
            if(entity instanceof Area) {
                this.searchTargets.add(entityID);
            }
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        if(this.searchTargets == null || this.searchTargets.isEmpty()) {
            return this;
        }

        Human agent = (Human)this.agentInfo.me();
        PathPlanning pathPlanning = null;
        if(agent.getStandardURN() == StandardEntityURN.AMBULANCE_TEAM) {
            pathPlanning = this.moduleManager.getModule("TacticsAmbulance.PathPlanning");
        } else if(agent.getStandardURN() == StandardEntityURN.FIRE_BRIGADE) {
            pathPlanning = this.moduleManager.getModule("TacticsFire.PathPlanning");
        } else if(agent.getStandardURN() == StandardEntityURN.POLICE_FORCE) {
            pathPlanning = this.moduleManager.getModule("TacticsPolice.PathPlanning");
        }
        if(pathPlanning == null) {
            return this;
        }

        if(this.needRest(agent)) {
            this.result = this.calcRest(agent, pathPlanning, this.searchTargets);
            if(this.result != null) {
                return this;
            }
        }

        pathPlanning.setFrom(agent.getPosition());
        pathPlanning.setDestination(this.searchTargets);
        List<EntityID> path = pathPlanning.calc().getResult();
        if (path != null && path.size() > 0) {
            this.result = new ActionMove(path);
        }
        return this;
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

    private Action calcRest(Human human, PathPlanning pathPlanning, Collection<EntityID> targets) {
        EntityID position = human.getPosition();
        Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
        int refugesSize = refuges.size();
        if(refuges.contains(position)) {
            return new ActionRest();
        }
        List<EntityID> firstResult = null;
        while(refuges.size() > 0) {
            pathPlanning.setFrom(position);
            pathPlanning.setDestination(refuges);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                if (firstResult == null) {
                    firstResult = new ArrayList<>(path);
                    if(targets == null || targets.isEmpty()) {
                        break;
                    }
                }
                EntityID refugeID = path.get(path.size() - 1);
                pathPlanning.setFrom(refugeID);
                pathPlanning.setDestination(targets);
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0) {
                    return new ActionMove(path);
                }
                refuges.remove(refugeID);
                //remove failed
                if (refugesSize == refuges.size()) {
                    break;
                }
                refugesSize = refuges.size();
            } else {
                break;
            }
        }
        return firstResult != null ? new ActionMove(firstResult) : null;
    }
}
