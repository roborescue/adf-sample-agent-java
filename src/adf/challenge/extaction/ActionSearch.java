package adf.challenge.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.challenge.SampleModuleKey;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionSearch extends ExtAction {


    private EntityID[] searchBuildingID;
    public ActionSearch(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager);
        this.agentInfo = agentInfo;
        this.searchBuildingID = null;
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.searchBuildingID = targets;
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = new ActionRest();
        if(searchBuildingID != null) {
            PathPlanning pathPlanning = null;
            if(this.agentInfo.me().getStandardURN() == StandardEntityURN.AMBULANCE_TEAM) {
                pathPlanning = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
            } else if(this.agentInfo.me().getStandardURN() == StandardEntityURN.FIRE_BRIGADE) {
                pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
            } else if(this.agentInfo.me().getStandardURN() == StandardEntityURN.POLICE_FORCE) {
                pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
            }
            if(pathPlanning != null) {
                List<EntityID> path = pathPlanning.setFrom(agentInfo.getPosition()).setDestination(searchBuildingID).calc().getResult();
                if (path != null) {
                    path.remove(path.size() - 1);
                    this.result = new ActionMove(path);
                }
            }
        }
        return this;
    }
}
