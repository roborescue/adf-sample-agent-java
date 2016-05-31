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
import adf.component.module.complex.HumanSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsAmbulance;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class ChallengeAmbulance extends TacticsAmbulance {

    private PathPlanning pathPlanning;
    private HumanSelector humanSelector;
    private Search search;

    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.CIVILIAN,
                StandardEntityURN.FIRE_BRIGADE,
                StandardEntityURN.POLICE_FORCE,
                StandardEntityURN.AMBULANCE_TEAM,
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
        moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_TRANSPORT);
        moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH);
        this.search.precompute(precomputeData);
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR);
        this.humanSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH);
        this.search.resume(precomputeData);
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR);
        this.humanSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH);
        this.search.preparate();
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR);
        this.humanSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.humanSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        Human injured = agentInfo.someoneOnBoard();
        if (injured != null) {
            return moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_TRANSPORT).setTarget(injured.getID()).calc().getAction();
        }

        EntityID target = this.humanSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_TRANSPORT).setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }
        // Nothing to do
        target = this.search.calc().getTarget();
        return moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH).setTarget(target).calc().getAction();
    }
}
