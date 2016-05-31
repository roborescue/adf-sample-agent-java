package adf.sample.module.complex;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.sample.SampleModuleKey;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanSelector;
import adf.sample.util.DistanceSorter;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SampleVictimSelector extends HumanSelector {

    private EntityID result;
    private int clusterIndex;

    public SampleVictimSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
        this.clusterIndex = -1;
    }

    @Override
    public HumanSelector calc() {
        Clustering clustering = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
        if(clustering == null) {
            this.result = this.failedClusteringCalc();
            return this;
        }
        if(this.clusterIndex == -1) {
            this.clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        }
        Collection<StandardEntity> elements = clustering.getClusterEntities(this.clusterIndex);

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
        targets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
        result = targets.isEmpty() ? this.failedClusteringCalc() : targets.get(0).getID();
        return this;
    }

    private EntityID failedClusteringCalc() {
        List<Human> targets = new ArrayList<>();
        for (StandardEntity next : worldInfo.getEntitiesOfType(
                StandardEntityURN.CIVILIAN,
                StandardEntityURN.FIRE_BRIGADE,
                StandardEntityURN.POLICE_FORCE,
                StandardEntityURN.AMBULANCE_TEAM)
                ) {
            Human h = (Human)next;
            if (agentInfo.getID() == h.getID()) {
                continue;
            }
            if (h.isHPDefined()
                    && h.isBuriednessDefined()
                    && h.isDamageDefined()
                    && h.isPositionDefined()
                    && h.getHP() > 0
                    && (h.getBuriedness() > 0 || h.getDamage() > 0)) {
                targets.add(h);
            }
        }
        targets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
        return targets.isEmpty() ? null : targets.get(0).getID();
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public HumanSelector precompute(PrecomputeData precomputeData) {
        return this;
    }

    @Override
    public HumanSelector resume(PrecomputeData precomputeData) {
        return this;
    }

    @Override
    public HumanSelector preparate() {
        return this;
    }
}
