package adf.sample.tactics.fire;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.precompute.PrecomputeData;
import adf.component.algorithm.cluster.Clustering;
import adf.component.algorithm.path.PathPlanner;
import adf.component.algorithm.target.TargetSelector;
import adf.component.tactics.TacticsFire;
import adf.sample.algorithm.cluster.PathBasedKMeans;
import adf.sample.algorithm.cluster.StandardKMeans;
import adf.sample.algorithm.path.DefaultPathPlanner;
import adf.sample.algorithm.target.BurningBuildingSelector;
import adf.sample.algorithm.target.cluster.ClusterSearchBuildingSelector;
import adf.sample.extaction.ActionFireFighting;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ClusterTacticsFire extends TacticsFire{

    private int maxWater;

    private PathPlanner pathPlanner;
    private TargetSelector<Building> burningBuildingSelector;
    private TargetSelector<Building> searchBuildingSelector;

    private Clustering clustering;
    private int clusterIndex;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.GAS_STATION
        );
        this.clustering = new PathBasedKMeans(agentInfo, worldInfo, scenarioInfo, worldInfo.getEntitiesOfType(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION
        )
        );
        this.pathPlanner = new DefaultPathPlanner(agentInfo, worldInfo, scenarioInfo);
        this.burningBuildingSelector = new BurningBuildingSelector(agentInfo, worldInfo, scenarioInfo);

        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.clusterIndex = -1;
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
        this.pathPlanner.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        this.searchBuildingSelector = new ClusterSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, this.clustering);
        this.searchBuildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
        this.pathPlanner.resume(precomputeData);
        this.clustering.resume(precomputeData);
        this.searchBuildingSelector = new ClusterSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, this.clustering);
        this.searchBuildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo) {
        this.clustering = new StandardKMeans(agentInfo, worldInfo, scenarioInfo, worldInfo.getEntitiesOfType(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION
        )
        );
        this.clustering.calc();
        this.searchBuildingSelector = new ClusterSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanner, this.clustering);
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        this.burningBuildingSelector.updateInfo();
        this.searchBuildingSelector.updateInfo();
        this.pathPlanner.updateInfo();

        // Are we currently filling with water?
        if (agentInfo.isWaterDefined() && agentInfo.getWater() < this.maxWater && agentInfo.getLocation().getStandardURN().equals(StandardEntityURN.REFUGE)) {
            return new ActionRest();
        }
        if(agentInfo.isWaterDefined() && agentInfo.getWater() < (this.maxWater / 5) && agentInfo.getLocation().getStandardURN().equals(StandardEntityURN.HYDRANT)) {
            this.pathPlanner.setFrom(agentInfo.getPosition());
            this.pathPlanner.setDist(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = this.pathPlanner.getResult();
            return path != null ? new ActionMove(path) : new ActionRest();
        }
        // Are we out of water?
        if (agentInfo.isWaterDefined() && agentInfo.getWater() == 0) {
            // Head for a refuge
            this.pathPlanner.setFrom(agentInfo.getPosition());
            this.pathPlanner.setDist(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = this.pathPlanner.getResult();
            /*if (path == null) {
                this.pathPlanner.setFrom(agentInfo.getPosition());
                this.pathPlanner.setDist(worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
                path = this.pathPlanner.getResult();
            }*/
            if(path != null) {
                return new ActionMove(path);
            }
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

        // Find all buildings that are on fire
        EntityID target = this.burningBuildingSelector.calc().getTarget();
        if(target != null) {
            Action action = new ActionFireFighting(worldInfo, agentInfo, scenarioInfo, this.pathPlanner, target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        /////////////////////////////////////////////////////
        EntityID searchBuildingID = this.searchBuildingSelector.calc().getTarget();
        if(searchBuildingID != null) {
            this.pathPlanner.setFrom(agentInfo.getPosition());
            List<EntityID> path = this.pathPlanner.setDist(searchBuildingID).getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }
        return new ActionRest();
    }
}
