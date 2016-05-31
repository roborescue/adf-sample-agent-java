package adf.challenge.tactics;

import adf.agent.action.Action;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.challenge.SampleModuleKey;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFire;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class ChallengeFire extends TacticsFire {

    private PathPlanning pathPlanning;

    private BuildingSelector buildingSelector;
    private Search search;

    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        //init ExtAction
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_FIREFIGHTING);
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_REFILL);
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_SEARCH);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH);
        this.search.precompute(precomputeData);
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR);
        this.buildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH);
        this.search.resume(precomputeData);
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR);
        this.buildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH);
        this.search.preparate();
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR);
        this.buildingSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);


        // Are we currently filling with water?
        // Are we out of water?
        Action action = moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_REFILL).calc().getAction();
        if(action != null) {
            return action;
        }

        // Find all buildings that are on fire
        EntityID target = this.buildingSelector.calc().getTarget();
        if(target != null) {
            action = moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_FIREFIGHTING).setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }
        // Nothing to do
        target = this.search.calc().getTarget();
        return moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH).setTarget(target).calc().getAction();
    }
}
