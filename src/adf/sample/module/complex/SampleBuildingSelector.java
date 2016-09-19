package adf.sample.module.complex;


import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class SampleBuildingSelector extends BuildingSelector {
    private int thresholdCompleted;
    private int thresholdRefill;
    private EntityID result;

    public SampleBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        int maxWater = scenarioInfo.getFireTankMaximum();
        int maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        this.thresholdCompleted = (maxWater / 10) * developData.getInteger("fire.threshold.completed", 10);
        this.thresholdRefill = maxExtinguishPower * developData.getInteger("fire.threshold.refill", 1);
        this.result = null;
    }

    @Override
    public BuildingSelector calc() {
        this.result = null;
        // refill
        this.result = this.calcRefill();
        if(this.result != null) {
            return this;
        }
        // select building
        Clustering clustering = this.moduleManager.getModule("TacticsFire.Clustering");
        if(clustering == null) {
            this.result = this.calcTargetInWorld();
            return this;
        }
        this.result = this.calcTargetInCluster(clustering);
        if(this.result == null) {
            this.result = this.calcTargetInWorld();
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private EntityID calcRefill() {
        FireBrigade fireBrigade = (FireBrigade)this.agentInfo.me();
        int water = fireBrigade.getWater();
        StandardEntityURN positionURN = this.worldInfo.getPosition(fireBrigade).getStandardURN();
        if(positionURN.equals(StandardEntityURN.REFUGE) && water < this.thresholdCompleted) {
            return fireBrigade.getPosition();
        }
        PathPlanning pathPlanning = this.moduleManager.getModule("TacticsFire.PathPlanning");
        if(positionURN.equals(StandardEntityURN.HYDRANT) && water < this.thresholdCompleted) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = pathPlanning.calc().getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            } else {
                return fireBrigade.getPosition();
            }
        }
        if (water <= this.thresholdRefill) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            pathPlanning.calc();
            List<EntityID> path = pathPlanning.getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            }
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
            pathPlanning.calc();
            path = pathPlanning.getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            }
        }
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private EntityID calcTargetInCluster(Clustering clustering) {
        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        Collection<StandardEntity> elements = clustering.getClusterEntities(clusterIndex);
        if(elements == null || elements.isEmpty()) {
            return null;
        }
        StandardEntity agent = this.agentInfo.me();
        EntityID nearBuildingID = null;
        int minDistance = Integer.MAX_VALUE;
        for (StandardEntity entity : elements) {
            if (entity instanceof Building && ((Building)entity).isOnFire()) {
                int distance = this.worldInfo.getDistance(agent, entity);
                if(distance < minDistance) {
                    minDistance = distance;
                    nearBuildingID = entity.getID();
                }
            }
        }
        return nearBuildingID;
    }

    private EntityID calcTargetInWorld() {
        Collection<StandardEntity> entities = this.worldInfo.getEntitiesOfType(
                StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        StandardEntity agent = this.agentInfo.me();
        EntityID nearBuildingID = null;
        int minDistance = Integer.MAX_VALUE;
        for (StandardEntity entity : entities) {
            if (((Building)entity).isOnFire()) {
                int distance = this.worldInfo.getDistance(agent, entity);
                if(distance < minDistance) {
                    minDistance = distance;
                    nearBuildingID = entity.getID();
                }
            }
        }
        return nearBuildingID;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public BuildingSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public BuildingSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public BuildingSelector preparate() {
        super.preparate();
        return this;
    }
}
