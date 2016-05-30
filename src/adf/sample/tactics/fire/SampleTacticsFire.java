package adf.sample.tactics.fire;

import adf.agent.action.Action;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import adf.component.tactics.TacticsFire;
import adf.sample.extaction.ActionFireFighting;
import adf.sample.extaction.ActionRefill;
import adf.sample.extaction.ActionSearchCivilian;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class SampleTacticsFire extends TacticsFire {

    private PathPlanning pathPlanning;

    private BuildingSelector burningBuildingSelector;
    private BuildingSelector searchBuildingSelector;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.GAS_STATION
        );
        //new SamplePathPlanning(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
        this.pathPlanning = (PathPlanning)moduleManager.getModuleInstance("adf.component.module.algorithm.PathPlanning");
        //new BurningBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
        this.burningBuildingSelector = (BuildingSelector)moduleManager.getModuleInstance("adf.sample.complex.targetselector.BuildingSelector");
        //new SearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
        this.searchBuildingSelector = (BuildingSelector)moduleManager.getModuleInstance("adf.sample.complex.targetselector.SearchBuildingSelector");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.burningBuildingSelector.updateInfo(messageManager);
        this.searchBuildingSelector.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);

        // Are we currently filling with water?
        // Are we out of water?
        Action action = new ActionRefill(agentInfo, worldInfo, scenarioInfo, this.pathPlanning).calc().getAction();
        if(action != null) {
            return action;
        }

        // cannot fire fighting
        if (agentInfo.isWaterDefined() && agentInfo.getWater() == 0) {
            // search civilian
            return new ActionSearchCivilian(agentInfo, this.pathPlanning, this.searchBuildingSelector).calc().getAction();
        }

        // Find all buildings that are on fire
        EntityID target = this.burningBuildingSelector.calc().getTarget();
        if(target != null) {
            action = new ActionFireFighting(agentInfo, worldInfo, scenarioInfo, this.pathPlanning, target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        return new ActionRest();
    }
}
