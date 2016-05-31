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
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFire;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ClusteringTacticsFire extends TacticsFire {

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
        moduleManager.getExtAction("ActionSearch");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.calc();
        this.clustering.precompute(precomputeData);
        this.burningBuildingSelector = moduleManager.getModule("adf.sample.module.complex.clustering.SampleBuildingSelector");
        this.burningBuildingSelector.precompute(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.SampleSearch");
        this.search.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.resume(precomputeData);
        this.burningBuildingSelector = moduleManager.getModule("adf.sample.module.complex.clustering.SampleBuildingSelector");
        this.burningBuildingSelector.resume(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.SampleSearch");
        this.search.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.StandardKMeans");
        this.clustering.calc();
        this.burningBuildingSelector = moduleManager.getModule("adf.sample.module.complex.clustering.SampleBuildingSelector");
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.SampleSearch");
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.burningBuildingSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        // Are we currently filling with water?
        // Are we out of water?
        Action action = moduleManager.getExtAction("ActionRefill").calc().getAction();
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
            return moduleManager.getExtAction("ActionSearch").calc().getAction();
        }

        // Find all buildings that are on fire
        EntityID target = this.burningBuildingSelector.calc().getTarget();
        if(target != null) {
            action = moduleManager.getExtAction("ActionFireFighting").setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }
        return new ActionRest();
    }
}
