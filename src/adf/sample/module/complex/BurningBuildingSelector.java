package adf.sample.module.complex;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.complex.BuildingSelector;
import adf.sample.util.DistanceSorter;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

public class BurningBuildingSelector extends BuildingSelector {

    private EntityID result;

    public BurningBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
    }

    @Override
    public BuildingSelector calc() {
        List<Building> buildingList = new ArrayList<>();
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(StandardEntityURN.BUILDING)) {
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
