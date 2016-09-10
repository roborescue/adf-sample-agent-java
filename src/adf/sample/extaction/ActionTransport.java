package adf.sample.extaction;

import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.BLOCKADE;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class ActionTransport extends ExtAction {

    private EntityID target;

    public ActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.target = null;
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
        if(this.target == null) {
            return this;
        }

        AmbulanceTeam ambulanceTeam = (AmbulanceTeam) this.agentInfo.me();
        EntityID ambulancePosition = ambulanceTeam.getPosition();
        StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
        Human transportHuman = this.agentInfo.someoneOnBoard();

        PathPlanning pathPlanning = this.moduleManager.getModule("TacticsAmbulance.PathPlanning");

        if(!(targetEntity instanceof Human)) {
            if(transportHuman != null) {
                if(ambulancePosition.getValue() == this.target.getValue()) {
                    this.result = new ActionUnload();
                } else {
                    pathPlanning.setFrom(ambulancePosition);
                    pathPlanning.setDestination(this.target);
                    List<EntityID> path = pathPlanning.calc().getResult();
                    if (path != null && path.size() > 0) {
                        this.result = new ActionMove(path);
                    }
                }
            } else {
                if (targetEntity.getStandardURN() == BLOCKADE) {
                    targetEntity = this.worldInfo.getEntity(((Blockade) targetEntity).getPosition());
                }
                pathPlanning.setFrom(ambulancePosition);
                pathPlanning.setDestination(targetEntity.getID());
                List<EntityID> path = pathPlanning.calc().getResult();
                if (path != null && path.size() > 0) {
                    this.result = new ActionMove(path);
                }
            }
            return this;
        }

        if(transportHuman != null) {
            if(targetEntity.getID().getValue() != transportHuman.getID().getValue()) {
                this.result = new ActionUnload();
                return this;
            }
            if(this.worldInfo.getEntity(ambulancePosition).getStandardURN() == REFUGE) {
                this.result = new ActionUnload();
            } else {
                pathPlanning.setFrom(ambulancePosition);
                pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
                List<EntityID> path = pathPlanning.calc().getResult();
                if (path != null && path.size() > 0) {
                    this.result = new ActionMove(path);
                }
            }
            return this;
        }

        Human targetHuman = (Human)targetEntity;
        if(targetHuman.isHPDefined() && targetHuman.getHP() == 0) {
            return this;
        }
        if(this.worldInfo.getPosition(targetHuman).getStandardURN() == REFUGE) {
            return this;
        }

        if(ambulancePosition.getValue() == targetHuman.getPosition().getValue()) {
            if(targetHuman.isBuriednessDefined() && targetHuman.getBuriedness() > 0) {
                this.result = new ActionRescue(targetHuman);
            } else if(targetHuman.getStandardURN() == CIVILIAN) {
                this.result = new ActionLoad(targetHuman.getID());
            }
        } else {
            pathPlanning.setFrom(ambulancePosition);
            pathPlanning.setDestination(targetHuman.getPosition());
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}
