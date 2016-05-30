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
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsPolice;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ClusteringTacticsPolice extends TacticsPolice {

    private PathPlanning pathPlanning;

    private BlockadeSelector blockadeSelector;
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
                StandardEntityURN.BLOCKADE
        );
        this.pathPlanning = moduleManager.getModule("adf.component.module.algorithm.PathPlanning");
        this.clusterIndex = -1;
        this.blockadeSelector = moduleManager.getModule("adf.component.module.complex.BlockadeSelector");
        //init ExtAction
        moduleManager.getExtAction("ActionExtClear");
        moduleManager.getExtAction("ActionSearchCivilian");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.calc();
        this.clustering.precompute(precomputeData);
        this.blockadeSelector.precompute(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
        this.search.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.resume(precomputeData);
        this.blockadeSelector.resume(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
        this.search.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.StandardKMeans");
        this.clustering.calc();
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.blockadeSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

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
            Action action = moduleManager.getExtAction("ActionExtClear").setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        return moduleManager.getExtAction("ActionSearchCivilian").calc().getAction();
    }
}
