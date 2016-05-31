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
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsPolice;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class SampleTacticsPolice extends TacticsPolice {

    private PathPlanning pathPlanning;

    private BlockadeSelector blockadeSelector;
    private Search search;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        //new SamplePathPlanning(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
        this.pathPlanning = moduleManager.getModule("adf.component.module.algorithm.PathPlanning");
        //new SampleBlockadeSelector(agentInfo, worldInfo, scenarioInfo, moduleManager);
        this.blockadeSelector = moduleManager.getModule("adf.component.module.complex.BlockadeSelector");
        //new SearchBuilding(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
        this.search = moduleManager.getModule("adf.sample.module.complex.SearchBuilding");
        //init ExtAction
        moduleManager.getExtAction("ActionExtClear");
        moduleManager.getExtAction("ActionSearch");
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
        this.pathPlanning.updateInfo(messageManager);
        this.blockadeSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        EntityID target = this.blockadeSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager.getExtAction("ActionExtClear").setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        return moduleManager.getExtAction("ActionSearch").calc().getAction();
    }
}
