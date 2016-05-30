package adf.sample.tactics.fire;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
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
import adf.component.tactics.TacticsFire;
import adf.sample.algorithm.clustering.PathBasedKMeans;
import adf.sample.algorithm.clustering.StandardKMeans;
import adf.sample.algorithm.pathplanning.SamplePathPlanning;
import adf.sample.complex.targetselector.clustering.ClusteringBurningBuildingSelector;
import adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector;
import adf.sample.extaction.ActionFireFighting;
import adf.sample.extaction.ActionRefill;
import adf.sample.extaction.ActionSearchCivilian;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ClusteringTacticsFire extends TacticsFire {

    private PathPlanning pathPlanning;

    private BuildingSelector burningBuildingSelector;
    private BuildingSelector searchBuildingSelector;

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
        this.pathPlanning = (PathPlanning)moduleManager.getModuleInstance("adf.component.module.algorithm.PathPlanning");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering = (Clustering) moduleManager.getModuleInstance("adf.sample.algorithm.clustering.PathBasedKMeans");
        this.clustering.calc();
        this.clustering.precompute(precomputeData);
        this.burningBuildingSelector = (BuildingSelector) moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringBurningBuildingSelector");
        this.burningBuildingSelector.precompute(precomputeData);
        this.searchBuildingSelector = (BuildingSelector) moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector");
        this.searchBuildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering = (Clustering) moduleManager.getModuleInstance("adf.sample.algorithm.clustering.PathBasedKMeans");
        this.clustering.resume(precomputeData);
        this.burningBuildingSelector = (BuildingSelector) moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringBurningBuildingSelector");
        this.burningBuildingSelector.resume(precomputeData);
        this.searchBuildingSelector = (BuildingSelector) moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector");
        this.searchBuildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.clustering = (Clustering) moduleManager.getModuleInstance("adf.sample.algorithm.clustering.StandardKMeans");
        this.clustering.calc();
        this.burningBuildingSelector = (BuildingSelector) moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringBurningBuildingSelector");
        this.searchBuildingSelector = (BuildingSelector) moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector");
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.burningBuildingSelector.updateInfo(messageManager);
        this.searchBuildingSelector.updateInfo(messageManager);

        // Are we currently filling with water?
        // Are we out of water?
        Action action = new ActionRefill(agentInfo, worldInfo, scenarioInfo, this.pathPlanning).calc().getAction();
        if(action != null) {
            return action;
        }

        if(this.clusterIndex == -1) {
            this.clusterIndex = this.clustering.getClusterIndex(agentInfo.getID());
        }
        Collection<StandardEntity> list = this.clustering.getClusterEntities(this.clusterIndex);
        if(!list.contains(agentInfo.me())) {
            List<EntityID> path =
                    this.pathPlanning.setFrom(agentInfo.getPosition()).setDestination(WorldUtil.convertToID(list)).getResult();
            if (path != null) {
                return new ActionMove(path);
            }
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
