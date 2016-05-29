package adf.sample.tactics.police;

import adf.agent.action.Action;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BlockadeSelector;
import adf.component.module.complex.BuildingSelector;
import adf.component.tactics.TacticsPolice;
import adf.sample.extaction.ActionExtClear;
import adf.sample.extaction.ActionSearchCivilian;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class SampleTacticsPolice extends TacticsPolice {

    private PathPlanning pathPlanning;

    private BlockadeSelector blockadeSelector;
    private BuildingSelector buildingSelector;
    private ModuleManager moduleManager;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        this.moduleManager = new ModuleManager(agentInfo, worldInfo, scenarioInfo);
        try {
            //new SamplePathPlanning(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
            this.pathPlanning = (PathPlanning)this.moduleManager.getModuleInstance("adf.component.module.algorithm.PathPlanning");
            //new SampleBlockadeSelector(agentInfo, worldInfo, scenarioInfo, moduleManager);
            this.blockadeSelector = (BlockadeSelector)this.moduleManager.getModuleInstance("adf.component.module.complex.BlockadeSelector");
            //new SearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
            this.buildingSelector = (BuildingSelector)this.moduleManager.getModuleInstance("adf.sample.complex.targetselector.SearchBuildingSelector");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

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
        this.pathPlanning.updateInfo(messageManager);
        this.blockadeSelector.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);

        EntityID target = this.blockadeSelector.calc().getTarget();
        if(target != null) {
            Action action = new ActionExtClear(agentInfo, worldInfo, this.pathPlanning, target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        return new ActionSearchCivilian(agentInfo, this.pathPlanning, this.buildingSelector).calc().getAction();
    }
}
