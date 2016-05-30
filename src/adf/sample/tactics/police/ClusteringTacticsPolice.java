package adf.sample.tactics.police;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BlockadeSelector;
import adf.component.module.complex.BuildingSelector;
import adf.component.tactics.TacticsPolice;
import adf.sample.algorithm.clustering.PathBasedKMeans;
import adf.sample.algorithm.clustering.StandardKMeans;
import adf.sample.algorithm.pathplanning.SamplePathPlanning;
import adf.sample.complex.targetselector.SampleBlockadeSelector;
import adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector;
import adf.sample.extaction.ActionExtClear;
import adf.sample.extaction.ActionSearchCivilian;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ClusteringTacticsPolice extends TacticsPolice {

    private PathPlanning pathPlanning;

    private BlockadeSelector blockadeSelector;
    private BuildingSelector buildingSelector;

    private Clustering clustering;
    private int clusterIndex;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        this.pathPlanning = (PathPlanning)moduleManager.getModuleInstance("adf.component.module.algorithm.PathPlanning");
        this.clusterIndex = -1;
        this.blockadeSelector = (BlockadeSelector)moduleManager.getModuleInstance("adf.component.module.complex.BlockadeSelector");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering = (Clustering) moduleManager.getModuleInstance("adf.sample.algorithm.clustering.PathBasedKMeans");
        this.clustering.calc();
        this.clustering.precompute(precomputeData);
        this.blockadeSelector.precompute(precomputeData);
        this.buildingSelector = (BuildingSelector)moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector");
        this.buildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering = (Clustering) moduleManager.getModuleInstance("adf.sample.algorithm.clustering.PathBasedKMeans");
        this.clustering.resume(precomputeData);
        this.blockadeSelector.resume(precomputeData);
        this.buildingSelector = (BuildingSelector)moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector");
        this.buildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.clustering = (Clustering) moduleManager.getModuleInstance("adf.sample.algorithm.clustering.StandardKMeans");
        this.clustering.calc();
        this.buildingSelector = (BuildingSelector)moduleManager.getModuleInstance("adf.sample.complex.targetselector.clustering.ClusteringSearchBuildingSelector");
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.blockadeSelector.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);

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
