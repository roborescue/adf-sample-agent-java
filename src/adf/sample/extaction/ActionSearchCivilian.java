package adf.sample.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionSearchCivilian extends ExtAction {


    public ActionSearchCivilian(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager);
        this.agentInfo = agentInfo;
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = new ActionRest();
        Search search = this.moduleManager.getModule("Search");
        EntityID searchBuildingID = search.calc().getTarget();
        if(searchBuildingID != null) {
            PathPlanning pathPlanning = this.moduleManager.getModule("PathPlanning");
            List<EntityID> path = pathPlanning.setFrom(agentInfo.getPosition()).setDestination(searchBuildingID).calc().getResult();
            if (path != null) {
                path.remove(path.size() - 1);
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}
