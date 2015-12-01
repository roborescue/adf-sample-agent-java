package adf.sample.tactics.police;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.precompute.PrecomputeData;
import adf.component.algorithm.path.PathPlanner;
import adf.component.algorithm.target.TargetSelector;
import adf.component.tactics.TacticsPolice;
import adf.sample.algorithm.path.SamplePathPlanner;
import adf.sample.algorithm.target.BlockadeSelector;
import adf.sample.algorithm.target.SearchBuildingSelector;
import adf.sample.extaction.ActionExtClear;
import adf.sample.extaction.ActionSearchCivilian;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class SampleTacticsPolice extends TacticsPolice {

    private PathPlanner pathPlanner;

    private TargetSelector<Blockade> blockadeSelector;
    private TargetSelector<Building> buildingSelector;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        this.pathPlanner = new SamplePathPlanner(agentInfo, worldInfo, scenarioInfo);
        this.blockadeSelector = new BlockadeSelector(agentInfo, worldInfo, scenarioInfo);
        this.buildingSelector = new SearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanner);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo) {
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        this.pathPlanner.updateInfo();
        this.blockadeSelector.updateInfo();
        this.buildingSelector.updateInfo();

        EntityID target = this.blockadeSelector.calc().getTarget();
        if(target != null) {
            Action action = new ActionExtClear(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        return new ActionSearchCivilian(agentInfo, this.pathPlanner, this.buildingSelector).calc().getAction();
    }






}
