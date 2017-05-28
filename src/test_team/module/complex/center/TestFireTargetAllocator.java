package test_team.module.complex.center;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.complex.FireTargetAllocator;
import rescuecore2.worldmodel.EntityID;

import java.util.HashMap;
import java.util.Map;

public class TestFireTargetAllocator extends FireTargetAllocator {
    public TestFireTargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public FireTargetAllocator resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public FireTargetAllocator preparate() {
        super.preparate();
        return this;
    }

    @Override
    public Map<EntityID, EntityID> getResult() {
        return new HashMap<>();
    }

    @Override
    public FireTargetAllocator calc() {
        return this;
    }

    @Override
    public FireTargetAllocator updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }
}
