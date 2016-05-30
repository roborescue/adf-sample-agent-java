package adf.challenge.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFire;
import rescuecore2.standard.entities.StandardEntityURN;

public class ChallengeFire extends TacticsFire {

    private PathPlanning pathPlanning;

    private BuildingSelector burningBuildingSelector;
    private Search search;

    private Clustering clustering;
    private int clusterIndex;

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
        this.clusterIndex = -1;
        this.pathPlanning = moduleManager.getModule("adf.component.module.algorithm.PathPlanning");
        //init ExtAction
        moduleManager.getExtAction("ActionRefill");
        moduleManager.getExtAction("ActionFireFighting");
        moduleManager.getExtAction("ActionSearchCivilian");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.calc();
        this.clustering.precompute(precomputeData);
        this.burningBuildingSelector = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringBurningBuildingSelector");
        this.burningBuildingSelector.precompute(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
        this.search.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.resume(precomputeData);
        this.burningBuildingSelector = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringBurningBuildingSelector");
        this.burningBuildingSelector.resume(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
        this.search.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.StandardKMeans");
        this.clustering.calc();
        this.burningBuildingSelector = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringBurningBuildingSelector");
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.burningBuildingSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        return new ActionRest();
    }
}
