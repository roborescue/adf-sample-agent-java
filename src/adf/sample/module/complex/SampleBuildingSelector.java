package adf.sample.module.complex;


import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import adf.sample.SampleModuleKey;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class SampleBuildingSelector extends BuildingSelector {
    private int thresholdCompleted;
    private int thresholdRefill;
    private EntityID result;

    public SampleBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        int maxWater = scenarioInfo.getFireTankMaximum();
        int maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        //use DevelopData
        this.thresholdCompleted = (maxWater / 10) * developData.getInteger("fire.threshold.refill", 10);
        this.thresholdRefill = maxExtinguishPower;
        this.result = null;
    }

    @Override
    public BuildingSelector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }

    @Override
    public BuildingSelector calc() {
        this.result = null;
        FireBrigade fireBrigade = (FireBrigade)this.agentInfo.me();
        int water = fireBrigade.getWater();
        StandardEntityURN positionURN = this.worldInfo.getPosition(fireBrigade).getStandardURN();
        PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);

        if(positionURN.equals(StandardEntityURN.REFUGE) && water < this.thresholdCompleted) {
            this.result = fireBrigade.getPosition();
            return this;
        }
        if(positionURN.equals(StandardEntityURN.HYDRANT) && water < this.thresholdCompleted) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            pathPlanning.calc();
            List<EntityID> path = pathPlanning.getResult();
            if(path != null && !path.isEmpty()) {
                this.result = path.get(path.size() - 1);
            } else {
                this.result = fireBrigade.getPosition();
            }
            return this;
        }
        if (water <= this.thresholdRefill) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            pathPlanning.calc();
            List<EntityID> path = pathPlanning.getResult();
            if(path != null && !path.isEmpty()) {
                this.result = path.get(path.size() - 1);
                return this;
            }
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
            pathPlanning.calc();
            path = pathPlanning.getResult();
            if(path != null && !path.isEmpty()) {
                this.result = path.get(path.size() - 1);
                return this;
            }
        }

        Clustering clustering = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
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

    private EntityID calcTargetInCluster(Clustering clustering) {
        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        Collection<StandardEntity> elements = clustering.getClusterEntities(clusterIndex);
        if(elements == null || elements.isEmpty()) {
            return null;
        }

        List<Building> buildingList = new ArrayList<>();
        for (StandardEntity next : elements) {
            if (next instanceof Building) {
                Building b = (Building)next;
                if (b.isOnFire()) {
                    buildingList.add(b);
                }
            }
        }
        // Sort by distance
        if(buildingList.size() > 0) {
            buildingList.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
            return buildingList.get(0).getID();
        }
        return null;
    }

    private EntityID calcTargetInWorld() {
        List<Building> buildingList = new ArrayList<>();
        Collection<StandardEntity> entities = this.worldInfo.getEntitiesOfType(
                StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        for (StandardEntity next : entities) {
            Building b = (Building)next;
            if (b.isOnFire()) {
                buildingList.add(b);
            }
        }
        // Sort by distance
        if(buildingList.size() > 0) {
            buildingList.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
            return buildingList.get(0).getID();
        }
        return null;
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private class DistanceSorter implements Comparator<StandardEntity> {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference) {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b) {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }
}
