package adf.sample.complex.targetselector.cluster;


import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.algorithm.Clustering;
import adf.component.complex.TargetSelector;
import adf.sample.complex.targetselector.DistanceSorter;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

public class ClusterBurningBuildingSelector extends TargetSelector<Building> {

    private EntityID result;

    private Clustering clustering;
    private int clusterIndex;

    public ClusterBurningBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, Clustering clustering) {
        super(ai, wi, si);
        this.clustering = clustering;
        this.clusterIndex = -1;
    }

    @Override
    public TargetSelector<Building> calc() {
        if(this.clusterIndex == -1) {
            this.clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        }

        List<Building> buildingList = new ArrayList<>();
        for (StandardEntity next : this.clustering.getClusterEntities(this.clusterIndex)) {
            if (next.getStandardURN().equals(StandardEntityURN.BUILDING)) {
                Building b = (Building)next;
                if (b.isOnFire()) {
                    buildingList.add(b);
                }
            }
        }
        // Sort by distance
        buildingList.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getLocation()));
        this.result = buildingList.isEmpty() ? null : buildingList.get(0).getID();
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }
}
