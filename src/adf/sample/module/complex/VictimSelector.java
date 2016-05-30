package adf.sample.module.complex;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.complex.HumanSelector;
import adf.sample.util.DistanceSorter;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

public class VictimSelector extends HumanSelector {

    private EntityID result;

    public VictimSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
    }

    @Override
    public HumanSelector calc() {
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
        result = targets.isEmpty() ? null : targets.get(0).getID();
        return this;
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
