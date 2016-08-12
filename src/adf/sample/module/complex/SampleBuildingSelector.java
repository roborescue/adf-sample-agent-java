package adf.sample.module.complex;


import adf.agent.debug.DebugData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.sample.SampleModuleKey;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.BuildingSelector;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SampleBuildingSelector extends BuildingSelector {

    private EntityID result;

    private int clusterIndex;

    public SampleBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DebugData debugData) {
        super(ai, wi, si, moduleManager, debugData);
        this.clusterIndex = -1;
    }

    @Override
    public BuildingSelector calc() {
        Clustering clustering = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
        if(this.clusterIndex == -1) {
            this.clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        }
        List<Building> buildingList = new ArrayList<>();
        for (StandardEntity next : clustering.getClusterEntities(this.clusterIndex)) {
            if (next.getStandardURN().equals(StandardEntityURN.BUILDING)) {
                Building b = (Building)next;
                if (b.isOnFire()) {
                    buildingList.add(b);
                }
            }
        }
        // Sort by distance
        buildingList.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
        this.result = buildingList.isEmpty() ? this.failedClusteringCalc() : buildingList.get(0).getID();
        return this;
    }

    private EntityID failedClusteringCalc() {
        List<Building> buildingList = new ArrayList<>();
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(StandardEntityURN.BUILDING)) {
            Building b = (Building)next;
            if (b.isOnFire()) {
                buildingList.add(b);
            }
        }
        // Sort by distance
        buildingList.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
        return buildingList.isEmpty() ? null : buildingList.get(0).getID();
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
