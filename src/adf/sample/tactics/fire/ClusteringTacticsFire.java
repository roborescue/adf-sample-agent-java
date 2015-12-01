package adf.sample.tactics.fire;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.precompute.PrecomputeData;
import adf.component.algorithm.Clustering;
import adf.component.algorithm.PathPlanner;
import adf.component.complex.TargetSelector;
import adf.component.tactics.TacticsFire;
import adf.sample.algorithm.clustering.PathBasedKMeans;
import adf.sample.algorithm.clustering.StandardKMeans;
import adf.sample.algorithm.pathplanning.SamplePathPlanner;
import adf.sample.complex.targetselector.cluster.ClusteringBurningBuildingSelector;
import adf.sample.complex.targetselector.cluster.ClusteringSearchBuildingSelector;
import adf.sample.extaction.ActionFireFighting;
import adf.sample.extaction.ActionRefill;
import adf.sample.extaction.ActionSearchCivilian;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ClusteringTacticsFire extends TacticsFire {

    private PathPlanner pathPlanner;

    private TargetSelector<Building> burningBuildingSelector;
    private TargetSelector<Building> searchBuildingSelector;

    private Clustering clustering;
    private int clusterIndex;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
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
        this.clustering = new PathBasedKMeans(agentInfo, worldInfo, scenarioInfo, worldInfo.getEntitiesOfType(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        )
        );
        this.clusterIndex = -1;
        this.pathPlanner = new SamplePathPlanner(agentInfo, worldInfo, scenarioInfo);
        this.burningBuildingSelector = new ClusteringBurningBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.clustering);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
        this.clustering.calc();
        this.pathPlanner.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        this.burningBuildingSelector.precompute(precomputeData);
        this.searchBuildingSelector = new ClusteringSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, this.clustering);
        this.searchBuildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
        this.pathPlanner.resume(precomputeData);
        this.clustering.resume(precomputeData);
        this.burningBuildingSelector.resume(precomputeData);
        this.searchBuildingSelector = new ClusteringSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, this.clustering);
        this.searchBuildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo) {
        this.clustering = new StandardKMeans(agentInfo, worldInfo, scenarioInfo, worldInfo.getEntitiesOfType(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        )
        );
        this.clustering.calc();
        this.searchBuildingSelector = new ClusteringSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, this.clustering);
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        this.pathPlanner.updateInfo();
        this.clustering.updateInfo();
        this.burningBuildingSelector.updateInfo();
        this.searchBuildingSelector.updateInfo();

        // Are we currently filling with water?
        // Are we out of water?
        Action action = new ActionRefill(agentInfo, worldInfo, scenarioInfo, this.pathPlanner).calc().getAction();
        if(action != null) {
            return action;
        }

        if(this.clusterIndex == -1) {
            this.clusterIndex = this.clustering.getClusterIndex(agentInfo.getID());
        }
        Collection<StandardEntity> list = this.clustering.getClusterEntities(this.clusterIndex);
        if(!list.contains(agentInfo.me())) {
            this.pathPlanner.setFrom(agentInfo.getPosition());
            List<EntityID> path = this.pathPlanner.setDist(WorldUtil.convertToID(list)).getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }

        // cannot fire fighting
        if (agentInfo.isWaterDefined() && agentInfo.getWater() == 0) {
            // search civilian
            return new ActionSearchCivilian(agentInfo, this.pathPlanner, this.searchBuildingSelector).calc().getAction();
        }

        // Find all buildings that are on fire
        EntityID target = this.burningBuildingSelector.calc().getTarget();
        if(target != null) {
            action = new ActionFireFighting(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, target).calc().getAction();
            if(action != null) {
                return action;
            }
        }
        return new ActionRest();
    }
}
