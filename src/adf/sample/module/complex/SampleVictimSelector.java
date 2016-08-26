package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanSelector;
import adf.sample.SampleModuleKey;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleVictimSelector extends HumanSelector {

    private EntityID result;

    public SampleVictimSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public HumanSelector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();

        }
        return this;
    }

    @Override
    public HumanSelector calc() {
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

        List<Human> targets = new ArrayList<>();
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE)) {
            Human h = (Human) next;
            if(elements.contains(this.worldInfo.getPosition(h)) || elements.contains(h)) {
                if (h.isHPDefined() && h.isBuriednessDefined() && h.getHP() > 0 && h.getBuriedness() > 0) {
                    targets.add(h);
                }
            }
        }
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN)) {
            Human h = (Human) next;
            if (elements.contains(this.worldInfo.getPosition(h)) || elements.contains(h)) {
                if (h.isHPDefined() && h.getHP() > 0) {
                    if (h.isBuriednessDefined() && h.getBuriedness() > 0) {
                        targets.add(h);
                    } else if (h.isDamageDefined() && h.getDamage() > 0
                            && this.worldInfo.getPosition(h).getStandardURN() != REFUGE) {
                        targets.add(h);
                    }
                }
            }
        }
        if(targets.size() > 0) {
            targets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
            return targets.get(0).getID();
        }
        return null;
    }

    private EntityID calcTargetInWorld() {
        List<Human> targets = new ArrayList<>();
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE)) {
            Human h = (Human) next;
            if(this.agentInfo.getID().getValue() == h.getID().getValue()) {
                continue;
            }
            if (h.isHPDefined() && h.isBuriednessDefined() && h.getHP() > 0 && h.getBuriedness() > 0) {
                targets.add(h);
            }
        }
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN)) {
            Human h = (Human) next;
            if (h.isHPDefined() && h.getHP() > 0) {
                if (h.isBuriednessDefined() && h.getBuriedness() > 0) {
                    targets.add(h);
                } else if (h.isDamageDefined() && h.getDamage() > 0
                        && this.worldInfo.getPosition(h).getStandardURN() != REFUGE) {
                    targets.add(h);
                }
            }
        }
        if(targets.size() > 0) {
            targets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.getPositionArea()));
            return targets.get(0).getID();
        }
        return null;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public HumanSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public HumanSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public HumanSelector preparate() {
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
