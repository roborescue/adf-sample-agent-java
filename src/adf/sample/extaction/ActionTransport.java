package adf.sample.extaction;

import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.debug.DebugData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.sample.SampleModuleKey;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class ActionTransport extends ExtAction {

    private Human target;

    public ActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DebugData debugData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, debugData);
        this.target = null;
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        if(targets != null) {
            for(EntityID target : targets) {
                StandardEntity entity = this.worldInfo.getEntity(target);
                if (entity instanceof Human) {
                    this.target = (Human) entity;
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
        StandardEntityURN positionURN = this.worldInfo.getPosition(ambulanceTeam).getStandardURN();
        if(this.target.getPosition().equals(ambulanceTeam.getID())) {
            if (positionURN.equals(REFUGE)) {
                this.result = new ActionUnload();
            } else {
                PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
                pathPlanning.setFrom(ambulanceTeam.getPosition());
                pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
                List<EntityID> path = pathPlanning.calc().getResult();
                if (path != null && path.size() > 0) {
                    this.result = new ActionMove(path);
                }
            }
            return this;
        }

        if(this.target.isHPDefined() && this.target.getHP() == 0) {
            return this;
        }
        if (this.target.getPosition().equals(ambulanceTeam.getPosition())) {
            if (this.target.getBuriedness() > 0) {
                this.result = new ActionRescue(this.target.getID());
            } else if (this.target.getStandardURN().equals(CIVILIAN) && !(this.worldInfo.getPosition(this.target).getStandardURN().equals(REFUGE))) {
                this.result = new ActionLoad(this.target.getID());
            }
        } else {
            PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
            pathPlanning.setFrom(ambulanceTeam.getPosition()).setDestination(this.target.getPosition());
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }

}
