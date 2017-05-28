package test_team.module.complex.center;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.complex.PoliceTargetAllocator;
import rescuecore2.worldmodel.EntityID;

import java.util.HashMap;
import java.util.Map;

public class TestPoliceTargetAllocator extends PoliceTargetAllocator {
    public TestPoliceTargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public PoliceTargetAllocator resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        return this;
    }

    @Override
    public PoliceTargetAllocator preparate() {
        super.preparate();
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        return this;
    }

    @Override
    public Map<EntityID, EntityID> getResult() {
        return new HashMap<>();
    }

    @Override
    public PoliceTargetAllocator calc() {
        return this;
    }

    @Override
    public PoliceTargetAllocator updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        return this;
    }
}
