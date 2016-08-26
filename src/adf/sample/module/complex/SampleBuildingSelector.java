package adf.sample.module.complex;


import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.BuildingSelector;
import adf.sample.SampleModuleKey;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class SampleBuildingSelector extends BuildingSelector {

    private EntityID result;

    public SampleBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public BuildingSelector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }

    @Override
    public BuildingSelector calc() {
        Clustering clustering = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
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
