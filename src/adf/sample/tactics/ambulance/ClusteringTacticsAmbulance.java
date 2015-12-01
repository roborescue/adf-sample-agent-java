package adf.sample.tactics.ambulance;


import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.precompute.PrecomputeData;
import adf.component.algorithm.Clustering;
import adf.component.algorithm.PathPlanning;
import adf.component.complex.TargetSelector;
import adf.component.tactics.TacticsAmbulance;
import adf.sample.algorithm.clustering.PathBasedKMeans;
import adf.sample.algorithm.clustering.StandardKMeans;
import adf.sample.algorithm.pathplanning.SamplePathPlanning;
import adf.sample.complex.targetselector.cluster.ClusteringSearchBuildingSelector;
import adf.sample.complex.targetselector.cluster.ClusteringVictimSelector;
import adf.sample.extaction.ActionSearchCivilian;
import adf.sample.extaction.ActionTransport;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ClusteringTacticsAmbulance extends TacticsAmbulance {

    private PathPlanning pathPlanning;

    private TargetSelector<Human> victimSelector;
    private TargetSelector<Building> buildingSelector;

    private Clustering clustering;
    private int clusterIndex;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
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
        this.pathPlanning = new SamplePathPlanning(agentInfo, worldInfo, scenarioInfo);
        this.victimSelector = new ClusteringVictimSelector(agentInfo, worldInfo, scenarioInfo, this.clustering);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
        this.clustering.calc();
        this.pathPlanning.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        this.victimSelector.precompute(precomputeData);
        this.buildingSelector = new ClusteringSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanning, this.clustering);
        this.buildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering.resume(precomputeData);
        this.victimSelector.resume(precomputeData);
        this.buildingSelector = new ClusteringSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanning, this.clustering);
        this.buildingSelector.resume(precomputeData);
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
        this.buildingSelector = new ClusteringSearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanning, this.clustering);
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        this.pathPlanning.updateInfo();
        this.clustering.updateInfo();
        this.victimSelector.updateInfo();
        this.buildingSelector.updateInfo();

        Human injured = agentInfo.someoneOnBoard();
        if (injured != null) {
            return new ActionTransport(agentInfo, worldInfo, this.pathPlanning, injured).calc().getAction();
        }

        if(this.clusterIndex == -1) {
            this.clusterIndex = this.clustering.getClusterIndex(agentInfo.getID());
        }
        Collection<StandardEntity> list = this.clustering.getClusterEntities(this.clusterIndex);
        if(!list.contains(agentInfo.me())) {
            this.pathPlanning.setFrom(agentInfo.getPosition());
            List<EntityID> path = this.pathPlanning.setDestination(WorldUtil.convertToID(list)).getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }

        // Go through targets (sorted by distance) and check for things we can do
        EntityID target = this.victimSelector.calc().getTarget();
        if(target != null) {
            Action action = new ActionTransport(agentInfo, worldInfo, this.pathPlanning, (Human)worldInfo.getEntity(target)).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        return new ActionSearchCivilian(agentInfo, this.pathPlanning, this.buildingSelector).calc().getAction();
    }
}
