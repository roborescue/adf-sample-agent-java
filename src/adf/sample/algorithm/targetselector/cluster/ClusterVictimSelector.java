package adf.sample.algorithm.targetselector.cluster;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.algorithm.clustering.Clustering;
import adf.component.algorithm.targetselector.TargetSelector;
import adf.sample.algorithm.targetselector.DistanceSorter;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClusterVictimSelector extends TargetSelector<Human> {

    private EntityID result;
    private Clustering clustering;
    private int clusterIndex;

    public ClusterVictimSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, Clustering clustering) {
        super(ai, wi, si);
        this.clustering = clustering;
        this.clusterIndex = -1;
    }

    @Override
    public TargetSelector<Human> calc() {
        if(this.clusterIndex == -1) {
            this.clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        }
        Collection<StandardEntity> elements = this.clustering.getClusterEntities(this.clusterIndex);

        List<Human> targets = new ArrayList<>();
        for (StandardEntity next : worldInfo.getEntitiesOfType(
                StandardEntityURN.CIVILIAN,
                StandardEntityURN.FIRE_BRIGADE,
                StandardEntityURN.POLICE_FORCE,
                StandardEntityURN.AMBULANCE_TEAM)
                ) {
            if (agentInfo.getID() == next.getID()) {
                continue;
            }
            Human h = (Human) next;
            if(elements.contains(this.worldInfo.getPosition(h)) || elements.contains(h)) {
                if (h.isHPDefined()
                        && h.isBuriednessDefined()
                        && h.isDamageDefined()
                        && h.isPositionDefined()
                        && h.getHP() > 0
                        && (h.getBuriedness() > 0 || h.getDamage() > 0)) {
                    targets.add(h);
                }
            }
        }
        targets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getLocation()));
        result = targets.isEmpty() ? null : targets.get(0).getID();
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }
}
