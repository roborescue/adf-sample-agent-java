package adf.sample.complex.targetselector;

import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class SearchBuildingSelector extends BuildingSelector {

    private Collection<EntityID> unexploredBuildings;
    private EntityID result;

    public SearchBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
        this.unexploredBuildings = new HashSet<>();
    }

    @Override
    public BuildingSelector updateInfo(MessageManager messageManager) {
        if(this.unexploredBuildings.isEmpty()) {
            for (StandardEntity next : this.worldInfo) {
                if(StandardEntityURN.BUILDING.equals(next.getStandardURN())) {
                    this.unexploredBuildings.add(next.getID());
                }
            }
        }
        for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
            this.unexploredBuildings.remove(next);
        }
        if(this.unexploredBuildings.isEmpty()) {
            for (StandardEntity next : this.worldInfo) {
                if(StandardEntityURN.BUILDING.equals(next.getStandardURN())) {
                    this.unexploredBuildings.add(next.getID());
                }
            }
        }
        return this;
    }

    @Override
    public BuildingSelector calc() {
        try {
            PathPlanning pathPlanning = (PathPlanning) this.moduleManager.getModuleInstance("adf.component.module.algorithm.PathPlanning");
            List<EntityID> path =
                    pathPlanning.setFrom(this.agentInfo.getPosition()).setDestination(this.unexploredBuildings).calc().getResult();
            if (path != null) {
                this.result = path.get(path.size() - 1);
            }
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
