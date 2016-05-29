package adf.sample.complex.targetselector.clustering;


import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.BuildingSelector;
import adf.sample.util.DistanceSorter;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

public class ClusteringBurningBuildingSelector extends BuildingSelector {

    private EntityID result;

    private int clusterIndex;

    public ClusteringBurningBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
        this.clusterIndex = -1;
    }

    @Override
    public BuildingSelector calc() {
        try {
            Clustering clustering = (Clustering) this.moduleManager.getModuleInstance("adf.component.module.algorithm.Clustering");
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
            this.result = buildingList.isEmpty() ? null : buildingList.get(0).getID();

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public BuildingSelector precompute(PrecomputeData precomputeData) {
        return this;
    }

    @Override
    public BuildingSelector resume(PrecomputeData precomputeData) {
        return this;
    }

    @Override
    public BuildingSelector preparate() {
        return this;
    }
}
