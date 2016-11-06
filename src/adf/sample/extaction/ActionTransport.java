package adf.sample.extaction;

import adf.agent.action.Action;
import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
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
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.BLOCKADE;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class ActionTransport extends ExtAction {
    private int thresholdRest;
    private int kernelTime;

    private EntityID target;

    public ActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.target = null;
        this.thresholdRest = developData.getInteger("ActionExtMove.rest", 100);
        this.kernelTime = scenarioInfo.getKernelTimesteps();
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.target = null;
        if(targets != null) {
            for(EntityID id : targets) {
                StandardEntity entity = this.worldInfo.getEntity(id);
                if(entity instanceof Human || entity instanceof Area) {
                    this.target = id;
                    return this;
                }
            }
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        if (this.target == null) {
            return this;
        }

        EntityID agentPosition = this.agentInfo.getPosition();
        Human transportHuman = this.agentInfo.someoneOnBoard();
        StandardEntity targetEntity = this.worldInfo.getEntity(this.target);

        PathPlanning pathPlanning = this.moduleManager.getModule("TacticsAmbulance.PathPlanning");

        if (targetEntity instanceof Human) {
            if (transportHuman != null) {
                if (targetEntity.getID().getValue() != transportHuman.getID().getValue()) {
                    this.result = new ActionUnload();
                } else if (this.worldInfo.getEntity(agentPosition).getStandardURN() == REFUGE) {
                    this.result = new ActionUnload();
                } else {
                    pathPlanning.setFrom(agentPosition);
                    pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
                    List<EntityID> path = pathPlanning.calc().getResult();
                    if (path != null && path.size() > 0) {
                        this.result = new ActionMove(path);
                    }
                }
                return this;
            }
            Human targetHuman = (Human) targetEntity;
            if (!targetHuman.isHPDefined() || targetHuman.getHP() == 0) {
                return this;
            }
            if(targetHuman.isPositionDefined()) {
                if (this.worldInfo.getPosition(targetHuman).getStandardURN() == REFUGE) {
                    return this;
                }
                if (agentPosition.getValue() == targetHuman.getPosition().getValue()) {
                    if (targetHuman.isBuriednessDefined() && targetHuman.getBuriedness() > 0) {
                        this.result = new ActionRescue(targetHuman);
                    } else if (targetHuman.getStandardURN() == CIVILIAN) {
                        this.result = new ActionLoad(targetHuman.getID());
                    }
                } else {
                    List<EntityID> path = pathPlanning.getResult(agentPosition, targetHuman.getPosition());
                    if (path != null && path.size() > 0) {
                        this.result = new ActionMove(path);
                    }
                }
            }
            return this;
        }
        if (targetEntity.getStandardURN() == BLOCKADE) {
            targetEntity = this.worldInfo.getEntity(((Blockade) targetEntity).getPosition());
        }
        if(!(targetEntity instanceof Area)) {
            return this;
        }
        if (transportHuman != null && agentPosition.getValue() == targetEntity.getID().getValue()) {
            this.result = new ActionUnload();
        } else {
            List<EntityID> path = pathPlanning.getResult(agentPosition, targetEntity.getID());
            if (path != null && path.size() > 0) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }

    private boolean needRest(Human agent) {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if(damage == 0 || hp == 0) {
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
