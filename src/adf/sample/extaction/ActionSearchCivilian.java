package adf.sample.extaction;

import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionSearchCivilian extends ExtAction {
    private AgentInfo agentInfo;

    private PathPlanning pathPlanning;

    private Search search;


    public ActionSearchCivilian(AgentInfo agentInfo, PathPlanning pathPlanning, Search search) {
        super();
        this.agentInfo = agentInfo;
        this.pathPlanning = pathPlanning;
        this.search = search;
    }

    @Override
    public ExtAction calc() {
        this.result = new ActionRest();
        EntityID searchBuildingID = this.search.calc().getTarget();
        if(searchBuildingID != null) {
            List<EntityID> path =
                    this.pathPlanning.setFrom(agentInfo.getPosition()).setDestination(searchBuildingID).calc().getResult();
            if (path != null) {
                path.remove(path.size() - 1);
                this.result = new ActionMove(path);
            }
        }
        return this;
    }
}
