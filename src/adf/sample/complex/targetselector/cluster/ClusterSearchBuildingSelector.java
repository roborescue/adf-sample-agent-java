package adf.sample.complex.targetselector.cluster;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.algorithm.Clustering;
import adf.component.algorithm.PathPlanner;
import adf.component.complex.TargetSelector;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ClusterSearchBuildingSelector extends TargetSelector<Building> {

    private PathPlanner pathPlanner;

    private Clustering clustering;
    private int clusterIndex;

    private Collection<EntityID> unexploredBuildings;
    private EntityID result;

    public ClusterSearchBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, PathPlanner pp, Clustering clustering) {
        super(ai, wi, si);
        this.pathPlanner = pp;
        this.clustering = clustering;
        this.init();
    }

    private void init() {
        this.clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());

        this.unexploredBuildings = new HashSet<>();
        for (StandardEntity next : this.clustering.getClusterEntities(this.clusterIndex)) {
            if(StandardEntityURN.BUILDING.equals(next.getStandardURN())) {
                this.unexploredBuildings.add(next.getID());
            }
        }
    }

    @Override
    public TargetSelector<Building> updateInfo() {
        for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
            this.unexploredBuildings.remove(next);
        }
        return this;
    }

    @Override
    public TargetSelector<Building> calc() {
        this.pathPlanner.setFrom(this.agentInfo.getPosition());
        List<EntityID> path = this.pathPlanner.setDist(this.unexploredBuildings).getResult();
        if (path != null) {
            this.result = path.get(path.size() - 1);
        }
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }
}