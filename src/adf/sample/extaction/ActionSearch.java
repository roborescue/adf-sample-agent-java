package adf.sample.extaction;

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

import java.util.ArrayList;
import java.util.List;

public class ActionSearch extends ExtAction {
    private List<EntityID> searchTargets;

    public ActionSearch(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DebugData debugData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, debugData);
        this.searchTargets = new ArrayList<>();
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.searchTargets.clear();
        for(EntityID entityID : targets) {
            StandardEntity entity = this.worldInfo.getEntity(entityID);
            if(entity instanceof Area) {
                this.searchTargets.add(entityID);
            } else if(entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
                this.searchTargets.add(((Blockade)entity).getPosition());
            } else if(entity instanceof Human) {
                this.searchTargets.add(((Human)entity).getPosition());
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

        PathPlanning pathPlanning = null;
        Human agent = (Human)this.agentInfo.me();
        if(agent.getStandardURN() == StandardEntityURN.AMBULANCE_TEAM) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
        } else if(agent.getStandardURN() == StandardEntityURN.FIRE_BRIGADE) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        } else if(agent.getStandardURN() == StandardEntityURN.POLICE_FORCE) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        }
        if(pathPlanning != null) {
            List<EntityID> path = pathPlanning.setFrom(agent.getPosition()).setDestination(this.searchTargets).calc().getResult();
            if (path != null && path.size() > 0) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}
